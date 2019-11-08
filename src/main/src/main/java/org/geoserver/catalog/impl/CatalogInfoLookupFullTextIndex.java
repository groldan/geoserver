package org.geoserver.catalog.impl;

import com.google.common.base.Stopwatch;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.Predicates;
import org.geotools.util.logging.Logging;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.sort.SortOrder;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * TODO:
 *
 * <ul>
 *   <li>commit (or flush) periodically
 *   <li>maybe update required fields only at {@link #doUpdate(Document)}?
 *   <li>at {@link #search}, escape special chars in lookup terms. i.e. (<code> + –
 * && || ! ( ) { } [ ] ^ ” ~ * ? : \ </code>) with a // backslash (e.g. {@code \\+})
 *   <li>Delegate whole (non full-text search) filters to this class as long all properties are
 *       supported?
 *   <li>re-index related objects when updating (e.g. when a workspace name changes, index
 *       properties like {@code resource.store.workspace.name} must be re-created
 * </ul>
 */
public class CatalogInfoLookupFullTextIndex {

    private static final Logger LOGGER = Logging.getLogger(CatalogInfoLookupFullTextIndex.class);
    private static final CustomizableThreadFactory THREAD_FACTORY =
            new CustomizableThreadFactory("CatalogFullTextIndex-");

    static {
        THREAD_FACTORY.setDaemon(true);
    }

    private static final CatalogPropertyAccessor propertyExtractor = new CatalogPropertyAccessor();

    private static final Set<String> SORTABLE_PROPERTY_NAMES =
            ImmutableSet.of(
                    "name",
                    "isolated",
                    "prefix",
                    "URI",
                    "type",
                    "workspace.name",
                    "enabled",
                    "URL",
                    "description",
                    "nativeName",
                    "prefixedName",
                    "projectionPolicy",
                    "SRS",
                    "title",
                    "namespace.name",
                    "namespace.prefix",
                    "store.name",
                    "nativeFormat",
                    "resource.store.name",
                    "resource.SRS",
                    "resource.store.workspace.name");

    private static class Command {

        static enum Action {
            OPEN,
            ADD,
            UPDATE,
            DELETE,
            COMMIT,
            CLOSE;
        }

        static final Command Commit = new Command(Action.COMMIT);
        static final Command End = new Command(null);

        final Action action;
        final Document doc;
        final String id;

        private Command(Action action) {
            this(action, null, null);
        }

        private Command(Action action, Document doc, String id) {
            this.action = action;
            this.doc = doc;
            this.id = id;
        }

        public static Command add(Document doc) {
            return new Command(Action.ADD, doc, null);
        }

        public static Command update(Document doc) {
            return new Command(Action.UPDATE, doc, null);
        }

        public static Command delete(String id) {
            return new Command(Action.DELETE, null, id);
        }

        public @Override String toString() {
            return String.format(
                    "%s %s", action, id != null ? id : (doc != null ? doc.get("id") : ""));
        }
    }

    private static class IndexWorker implements Runnable {

        private final CatalogInfoLookupFullTextIndex indexer;
        private final BlockingQueue<Command> producer = new LinkedBlockingQueue<>();

        private final AtomicBoolean enabled = new AtomicBoolean(true);
        private final AtomicInteger uncommittedCount = new AtomicInteger();

        IndexWorker(CatalogInfoLookupFullTextIndex indexer) {
            this.indexer = indexer;
        }

        public void run(Command cmd) {
            if (enabled.get()) producer.add(cmd);
        }

        public @Override void run() {
            while (enabled.get()) {
                Command command;
                try {
                    command = producer.take();
                    if (command == Command.End) {
                        enabled.set(false);
                        return;
                    }
                    switch (command.action) {
                        case ADD:
                            indexer.doAdd(command.doc);
                            uncommittedCount.incrementAndGet();
                            break;
                        case DELETE:
                            indexer.doDelete(command.id);
                            uncommittedCount.incrementAndGet();
                            break;
                        case UPDATE:
                            indexer.doUpdate(command.doc);
                            uncommittedCount.incrementAndGet();
                            break;
                        case COMMIT:
                            final int uncommitted = uncommittedCount.getAndSet(0);
                            if (uncommitted > 0) {
                                Stopwatch sw = Stopwatch.createStarted();
                                indexer.doCommit();
                                System.err.printf(
                                        "--> Text index: Committed %,d pending changes in %s...\n",
                                        uncommitted, sw.stop());
                            }
                            break;
                        default:
                            break;
                    }
                } catch (InterruptedException e) {
                    String msg =
                            String.format(
                                    "Full Text Search worker interrupted. Pending actions: "
                                            + producer);
                    LOGGER.log(Level.SEVERE, msg, e);
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean alive = new AtomicBoolean(false);
    private ScheduledExecutorService workerExecutorService;
    private IndexWorker worker;
    private FSDirectory idx;
    private IndexWriter indexWriter; // thread-safe

    private final Path indexPath;

    private volatile boolean autocommit;

    public CatalogInfoLookupFullTextIndex(Path indexPath) {
        Objects.requireNonNull(indexPath);
        this.indexPath = indexPath;
    }

    public void setAutocommit(boolean autocommit) {
        System.err.println("->>>>>>>>>>>> Catalog Indexing: autocommit:" + autocommit);
        this.autocommit = autocommit;
    }

    public boolean isAutocommit() {
        return this.autocommit;
    }

    public boolean isOpen() {
        return open.get();
    }

    public void open() throws IOException {
        boolean wasClosed = open.compareAndSet(false, true);
        if (wasClosed) {
            try {
                doOpen();
            } catch (IOException e) {
                e.printStackTrace();
                doClose();
                open.set(false);
                throw e;
            }
            alive.set(true);
        }
    }

    void doOpen() throws IOException {
        idx = MMapDirectory.open(indexPath);
        IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
        conf.setOpenMode(OpenMode.CREATE_OR_APPEND);
        indexWriter = new IndexWriter(idx, conf);
        // call commit() to avoid a "org.apache.lucene.index.IndexNotFoundException: no
        // segments* file found in MMapDirectory@<path>" exception when opening a reader
        // on an empty directory
        indexWriter.commit();
        workerExecutorService = Executors.newScheduledThreadPool(2, THREAD_FACTORY);
        worker = new IndexWorker(this);
        workerExecutorService.submit(worker);

        long delay = 1;
        TimeUnit delayUnits = TimeUnit.SECONDS;
        workerExecutorService.scheduleWithFixedDelay(this::autoCommit, delay, delay, delayUnits);
    }

    public void close() throws IOException {
        boolean wasOpen = open.compareAndSet(true, false);
        if (wasOpen) {
            worker.run(Command.Commit);
            worker.run(Command.End);
            try {
                doClose();
            } finally {
                alive.set(false);
            }
        }
    }

    void doClose() throws IOException {
        if (workerExecutorService != null) {
            workerExecutorService.shutdown();
            while (!workerExecutorService.isTerminated()) {
                try {
                    workerExecutorService.awaitTermination(2, TimeUnit.SECONDS);
                    LOGGER.info("Full text search index shut down.");
                } catch (InterruptedException e) {
                    LOGGER.warning("Awaiting full text search index shutdown...");
                }
            }
        }

        FSDirectory idx = this.idx;
        IndexWriter indexWriter = this.indexWriter;
        this.idx = null;
        this.indexWriter = null;
        boolean swallowIOException = true;
        Closeables.close(indexWriter, swallowIOException);
        Closeables.close(idx, swallowIOException);
    }

    public void add(CatalogInfo info) {
        Objects.requireNonNull(info);
        checkAlive();
        Document document = createDocument(info);
        worker.run(Command.add(document));
    }

    void doAdd(Document doc) throws IOException {
        indexWriter.addDocument(doc);
    }

    public void commit() {
        checkAlive();
        worker.run(Command.Commit);
    }

    private void autoCommit() {
        if (this.autocommit) {
            checkAlive();
            worker.run(Command.Commit);
        }
    }

    void doCommit() throws IOException {
        indexWriter.commit();
    }

    public void remove(String id) throws IOException {
        Objects.requireNonNull(id);
        checkAlive();
        worker.run(Command.delete(id));
    }

    void doDelete(String... ids) throws IOException {
        // Query query = new TermInSetQuery(new Term("field", "foo"), new Term("field",
        // "bar"));
        Builder query = new BooleanQuery.Builder();
        for (String id : ids) {
            query.add(new TermQuery(new Term("id", id)), Occur.SHOULD);
        }
        indexWriter.deleteDocuments(query.build());
    }

    public void update(CatalogInfo info) throws IOException {
        Objects.requireNonNull(info);
        checkAlive();
        worker.run(Command.update(createDocument(info)));
    }

    void doUpdate(Document doc) throws IOException {
        indexWriter.updateDocument(new Term("id", doc.get("id")), doc);
    }

    private void checkAlive() {
        if (!alive.get()) {
            throw new IllegalStateException("Catalog full text index is not running");
        }
    }

    public Stream<String> search(String... terms) throws IOException {
        return search(Arrays.asList(terms));
    }

    public int hitCount(Iterable<String> searchTerms) throws IOException {
        return hitCount(CatalogInfo.class, searchTerms);
    }

    public int hitCount(Class<? extends CatalogInfo> clazz, Iterable<String> searchTerms)
            throws IOException {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(searchTerms);
        checkAlive();
        BooleanQuery query = buildQuery(clazz, searchTerms);
        try (DirectoryReader indexReader = DirectoryReader.open(idx)) {
            IndexSearcher searcher = new IndexSearcher(indexReader);
            // TotalHitCountCollector hitsCollector = new TotalHitCountCollector();
            // searcher.search(query, hitsCollector);
            // int totalHits = hitsCollector.getTotalHits();
            int totalHits = searcher.count(query);
            return totalHits;
        }
    }

    public Stream<String> search(List<String> terms) throws IOException {
        return search(CatalogInfo.class, terms);
    }

    public Stream<String> search(Class<? extends CatalogInfo> clazz, Iterable<String> terms)
            throws IOException {
        return search(clazz, terms, null, null, null);
    }

    public Stream<String> search(
            Class<? extends CatalogInfo> clazz,
            Iterable<String> terms,
            final @Nullable Integer offset,
            final @Nullable Integer count,
            final @Nullable SortBy[] sortBy)
            throws IOException {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(terms);
        // TODO: escape special chars (+ – && || ! ( ) { } [ ] ^ ” ~ * ? : \) with a
        // backslash (e.g. \\+)
        checkAlive();
        final BooleanQuery query = buildQuery(clazz, terms);
        final @Nullable Sort sortOrder = toLuceneSort(sortBy);

        final DirectoryReader indexReader = DirectoryReader.open(idx);
        Stream<String> idStream;
        try {
            if (count == null) {
                idStream = pagingStream(indexReader, query, offset == null ? 0 : offset, sortOrder);
            } else {
                idStream =
                        fetchPage(
                                indexReader, query, offset == null ? 0 : offset, count, sortOrder);
            }
        } catch (IOException | RuntimeException e) {
            indexReader.close();
            throw e;
        }
        idStream =
                idStream.onClose(
                        () -> {
                            try {
                                indexReader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
        return idStream;
    }

    private Stream<String> pagingStream(
            final DirectoryReader indexReader,
            final BooleanQuery query,
            int offset,
            final @Nullable Sort sortOrder)
            throws IOException {
        Iterator<String> pagingIterator =
                new AbstractIterator<String>() {
                    final int pageSize = 1_000;
                    private IndexSearcher searcher = new IndexSearcher(indexReader);
                    private final Query searchQuery = query;
                    private final Sort sort = sortOrder;
                    private final Set<String> fieldsToLoad = Collections.singleton("id");

                    private ScoreDoc lastScoreDoc;

                    private Iterator<ScoreDoc> page = Collections.emptyIterator();

                    protected @Override String computeNext() {
                        if (page.hasNext()) {
                            lastScoreDoc = page.next();
                            try {
                                Document doc = searcher.doc(lastScoreDoc.doc, fieldsToLoad);
                                return doc.get("id");
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        }
                        page = nextPage();
                        if (!page.hasNext()) {
                            return endOfData();
                        }
                        return computeNext();
                    }

                    private Iterator<ScoreDoc> nextPage() {
                        // Stopwatch sw = Stopwatch.createStarted();
                        TopDocs topDocs;
                        ScoreDoc[] scoreDocs;
                        try {
                            System.err.printf(
                                    "Querying lucene: offset[%,d], limit[%,d], filter[%s], sort[%s]%n", //
                                    lastScoreDoc == null ? offset : lastScoreDoc.doc + 1, //
                                    pageSize,
                                    searchQuery,
                                    sort);
                            // not sure how to do paging properly... revisit
                            // if (offset != null) {
                            // int start = offset.intValue();
                            // if (start == 0) {
                            // start = 1;
                            // }
                            // if (sort == null) {
                            // topDocs = searcher.search(searchQuery, start);
                            // } else {
                            // topDocs = searcher.search(searchQuery, start,
                            // sort);
                            // }
                            // scoreDocs = topDocs.scoreDocs;
                            // if (scoreDocs == null || scoreDocs.length < start)
                            // {
                            // return Collections.emptyIterator();
                            // }
                            // lastScoreDoc = scoreDocs[start - 1];
                            // }
                            if (lastScoreDoc == null) {
                                if (sort == null) {
                                    topDocs = searcher.search(searchQuery, pageSize);
                                } else {
                                    topDocs = searcher.search(searchQuery, pageSize, sort);
                                }
                            } else {
                                if (sort == null) {
                                    topDocs =
                                            searcher.searchAfter(
                                                    lastScoreDoc, searchQuery, pageSize);
                                } else {
                                    topDocs =
                                            searcher.searchAfter(
                                                    lastScoreDoc, searchQuery, pageSize, sort);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            throw new RuntimeException(e);
                        }
                        // System.err.printf("### lucene search: %s, hits: %,d ###\n",
                        // sw.stop(), topDocs.scoreDocs.length);

                        scoreDocs = topDocs.scoreDocs;
                        if (scoreDocs == null || scoreDocs.length == 0) {
                            return Collections.emptyIterator();
                        }
                        return Arrays.asList(scoreDocs).iterator();
                    }
                };

        Stream<String> idStream = Streams.stream(pagingIterator);
        return idStream;
    }

    private Stream<String> fetchPage(
            final DirectoryReader indexReader,
            BooleanQuery query,
            int offset,
            int count,
            @Nullable Sort sortOrder)
            throws IOException {

        IndexSearcher searcher = new IndexSearcher(indexReader);
        int total = offset + count;
        TopDocs topDocs;
        if (sortOrder == null) {
            topDocs = searcher.search(query, total);
        } else {
            topDocs = searcher.search(query, total, sortOrder);
        }
        ScoreDoc[] scoreDocs = topDocs.scoreDocs;
        if (scoreDocs == null || scoreDocs.length == 0) {
            return Stream.empty();
        }

        final Set<String> fieldsToLoad = Collections.singleton("id");
        return Arrays.stream(scoreDocs)
                .skip(offset)
                .map(
                        d -> {
                            try {
                                return searcher.doc(d.doc, fieldsToLoad);
                            } catch (IOException e) {
                                e.printStackTrace();
                                throw new RuntimeException(e);
                            }
                        })
                .map(doc -> doc.get("id"));
    }

    private @Nullable Sort toLuceneSort(SortBy[] sortBy) {
        if (sortBy == null || sortBy.length == 0) {
            return null;
        }
        SortField[] fields = new SortField[sortBy.length];
        for (int i = 0; i < sortBy.length; i++) {
            SortBy sb = sortBy[i];
            Objects.requireNonNull(sb);
            String propertyName = sb.getPropertyName().getPropertyName();
            Type type = SortField.Type.STRING;
            boolean reverse = sb.getSortOrder() == SortOrder.DESCENDING;
            SortField sortField = new SortField(propertyName, type, reverse);
            fields[i] = sortField;
        }
        Sort sort = new Sort(fields);
        return sort;
    }

    private BooleanQuery buildQuery(Class<? extends CatalogInfo> clazz, Iterable<String> terms) {
        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        if (!CatalogInfo.class.equals(clazz)) { // all
            String typeOf = typesOf(clazz).toLowerCase();
            String[] types = typeOf.split(",");
            BooleanQuery.Builder typeFilter = new BooleanQuery.Builder();
            typeFilter.setMinimumNumberShouldMatch(0);
            for (String type : types) {
                typeFilter.add(new TermQuery(new Term("type", type)), Occur.SHOULD);
            }
            mainQuery.add(typeFilter.build(), Occur.MUST);
        }

        BooleanQuery.Builder contentFilter = new BooleanQuery.Builder();
        for (String pattern : terms) {
            Term term = new Term(Predicates.ANY_TEXT.getPropertyName(), pattern.toLowerCase());
            WildcardQuery wq = new WildcardQuery(term);
            contentFilter.add(wq, Occur.SHOULD);
        }
        mainQuery.add(contentFilter.build(), Occur.MUST);

        final BooleanQuery query = mainQuery.build();
        return query;
    }

    private final ConcurrentHashMap<Class<? extends CatalogInfo>, String> storedTypeNames =
            new ConcurrentHashMap<>();

    private Document createDocument(CatalogInfo info) {
        Set<String> propertyNames = CatalogPropertyAccessor.fullTextProperties(info);
        Document doc = new Document();
        // id stored with the index
        StringField idField = new StringField("id", info.getId(), Store.YES);
        // type stored with the index
        String typeFieldValue = typesOf(info);
        TextField typeField = new TextField("type", typeFieldValue, Store.YES);

        doc.add(idField);
        doc.add(typeField);

        StringBuilder anyText = new StringBuilder();
        propertyNames.forEach(
                propertyName -> {
                    Object value = propertyExtractor.getProperty(info, propertyName);
                    // add the field for sorting
                    if (SORTABLE_PROPERTY_NAMES.contains(propertyName)) {
                        BytesRef fieldBytes = toBytesRefForSortedField(value);
                        Field sortingField = new SortedDocValuesField(propertyName, fieldBytes);
                        doc.add(sortingField);
                    }
                    // and contribute to the full text search field only if non null
                    appendAnytext(value, anyText);
                });
        String allText = anyText.toString();
        // anyText not stored with the index, it's the big CLOB we're tokenizing and
        // searching for
        TextField anyTextField =
                new TextField(Predicates.ANY_TEXT.getPropertyName(), allText, Store.NO);
        doc.add(anyTextField);

        return doc;
    }

    private void appendAnytext(Object value, StringBuilder anyText) {
        if (value == null || value instanceof Boolean) return;
        if (value instanceof CharSequence) {
            if (anyText.length() > 0) {
                anyText.append('\n');
            }
            anyText.append((CharSequence) value);
            return;
        }
        if (value instanceof Enum<?> || value instanceof Number) {
            if (anyText.length() > 0) {
                anyText.append('\n');
            }
            anyText.append(value);
            return;
        }
        if (value instanceof Collection) {
            ((Collection<?>) value).forEach(elem -> appendAnytext(elem, anyText));
            return;
        }
    }

    private static final byte[] BYTES_NULL = {};
    private static final byte[] BYTES_FALSE = {0};
    private static final byte[] BYTES_TRUE = {1};

    private BytesRef toBytesRefForSortedField(@Nullable Object value) {
        if (value == null) {
            // a null value still needs to be a hit when sorting by a given property, so we
            // use an empty byte array to represent it
            return new BytesRef(BYTES_NULL);
        }
        if (value instanceof CharSequence) return new BytesRef((CharSequence) value);
        if (value instanceof Boolean)
            // return new BytesRef(((Boolean) value).booleanValue() ? BYTES_TRUE :
            // BYTES_FALSE);
            return new BytesRef(Boolean.class.cast(value).booleanValue() ? "true" : "false");
        if (value instanceof Number) {
            byte[] rawVal = null;
            if (value instanceof Byte) rawVal = new byte[] {Byte.class.cast(value).byteValue()};
            else if (value instanceof Short)
                rawVal = Shorts.toByteArray(Short.class.cast(value).shortValue());
            else if (value instanceof Integer)
                rawVal = Ints.toByteArray(Integer.class.cast(value).intValue());
            else if (value instanceof Long)
                rawVal = Longs.toByteArray(Long.class.cast(value).longValue());
            else if (value instanceof Float)
                rawVal =
                        Ints.toByteArray(
                                Float.floatToIntBits(Float.class.cast(value).floatValue()));
            else if (value instanceof Double)
                rawVal =
                        Longs.toByteArray(
                                Double.doubleToLongBits(Double.class.cast(value).doubleValue()));
            else if (value instanceof BigInteger)
                rawVal = BigInteger.class.cast(value).toByteArray();
            else if (value instanceof BigDecimal)
                rawVal = BigDecimal.class.cast(value).toBigInteger().toByteArray();

            return rawVal == null ? new BytesRef(value.toString()) : new BytesRef(rawVal);
        }
        // System.err.printf("Value is not string nor number: %s (%s)%n", value,
        // value.getClass().getName());
        return new BytesRef(value.toString());
    }

    private String typesOf(CatalogInfo info) {
        return typesOf(ModificationProxy.unwrap(info).getClass());
    }

    private String typesOf(Class<? extends CatalogInfo> type) {
        return storedTypeNames.computeIfAbsent(
                type,
                clazz -> {
                    ClassMappings mappings = ClassMappings.fromInterface(clazz);
                    if (mappings == null) {
                        mappings = ClassMappings.fromImpl(clazz);
                    }
                    // REVISIT: this doesn't always return all the interfaces of a class
                    Class<? extends Info>[] concreteInterfaces = mappings.concreteInterfaces();
                    if (concreteInterfaces.length == 1) {
                        return concreteInterfaces[0].getSimpleName();
                    }
                    String[] names = new String[concreteInterfaces.length];
                    for (int i = 0; i < names.length; i++) {
                        names[i] = concreteInterfaces[i].getSimpleName();
                    }
                    Arrays.sort(names);
                    return String.join(",", names);
                });
    }
}
