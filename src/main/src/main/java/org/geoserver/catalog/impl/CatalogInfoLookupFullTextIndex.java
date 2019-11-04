package org.geoserver.catalog.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.Predicates;
import org.geotools.util.logging.Logging;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;

public class CatalogInfoLookupFullTextIndex {

    private static final Logger LOGGER = Logging.getLogger(CatalogInfoLookupFullTextIndex.class);
    private static final CustomizableThreadFactory THREAD_FACTORY = new CustomizableThreadFactory(
            "CatalogFullTextIndex-");
    static {
        THREAD_FACTORY.setDaemon(true);
    }

    private static final CatalogPropertyAccessor propertyExtractor = new CatalogPropertyAccessor();

    private static class Command {

        static enum Action {
            OPEN, ADD, UPDATE, DELETE, COMMIT, CLOSE;
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
            return String.format("%s %s", action, id != null ? id : (doc != null ? doc.get("id") : ""));
        }
    }

    private static class IndexWorker implements Runnable {

        private final CatalogInfoLookupFullTextIndex indexer;
        private final BlockingQueue<Command> producer = new LinkedBlockingQueue<>();

        IndexWorker(CatalogInfoLookupFullTextIndex indexer) {
            this.indexer = indexer;
        }

        public void run(Command cmd) {
            producer.add(cmd);
        }

        public @Override void run() {
            while (true) {
                Command command;
                try {
                    command = producer.take();
                    if (command == Command.End) {
                        return;
                    }
                    switch (command.action) {
                    case ADD:
                        indexer.doAdd(command.doc);
                        break;
                    case DELETE:
                        indexer.doDelete(command.id);
                        break;
                    case UPDATE:
                        indexer.doUpdate(command.doc);
                        break;
                    case COMMIT:
                        indexer.doCommit();
                        break;
                    default:
                        break;
                    }
                } catch (InterruptedException e) {
                    String msg = String.format("Full Text Search worker interrupted. Pending actions: " + producer);
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
    private ExecutorService workerThread;
    private IndexWorker worker;
    private FSDirectory idx;
    private IndexWriter indexWriter;

    private final Path indexPath;

    public CatalogInfoLookupFullTextIndex(Path indexPath) {
        Objects.requireNonNull(indexPath);
        this.indexPath = indexPath;
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

        workerThread = Executors.newSingleThreadExecutor(THREAD_FACTORY);
        worker = new IndexWorker(this);
        workerThread.submit(worker);
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
        workerThread.shutdown();
        while (!workerThread.isTerminated()) {
            try {
                workerThread.awaitTermination(2, TimeUnit.SECONDS);
                LOGGER.info("Full text search index shut down.");
            } catch (InterruptedException e) {
                LOGGER.warning("Awaiting full text search index shutdown...");
            }
        }

        FSDirectory idx = this.idx;
        IndexWriter indexWriter = this.indexWriter;
        this.idx = null;
        this.indexWriter = null;
        if (indexWriter != null) {
            try {
                indexWriter.close();
            } finally {
                idx.close();
            }
        }
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

    void doCommit() throws IOException {
        indexWriter.commit();
    }

    public Stream<String> search(String... terms) throws IOException {
        return search(Arrays.asList(terms));
    }

    public int hitCount(Iterable<String> searchTerms) throws IOException {
        return hitCount(CatalogInfo.class, searchTerms);
    }

    public int hitCount(Class<? extends CatalogInfo> clazz, Iterable<String> searchTerms) throws IOException {
        checkAlive();
        BooleanQuery query = buildQuery(clazz, searchTerms);
        try (IndexReader indexReader = DirectoryReader.open(idx)) {
            IndexSearcher searcher = new IndexSearcher(indexReader);
            TotalHitCountCollector hitsCollector = new TotalHitCountCollector();
            searcher.search(query, hitsCollector);
            int totalHits = hitsCollector.getTotalHits();
            return totalHits;
        }
    }

    public Stream<String> search(List<String> terms) throws IOException {
        return search(CatalogInfo.class, terms);
    }

    public Stream<String> search(Class<? extends CatalogInfo> clazz, Iterable<String> terms) throws IOException {
        checkAlive();
        final BooleanQuery query = buildQuery(clazz, terms);
        final IndexReader indexReader = DirectoryReader.open(idx);

        Iterator<String> pagingIterator = new AbstractIterator<String>() {
            final int pageSize = 1000;

            private IndexSearcher searcher = new IndexSearcher(indexReader);
            private final Query searchQuery = query;

            private ScoreDoc lastScoreDoc;

            private Iterator<ScoreDoc> page = Collections.emptyIterator();

            protected @Override String computeNext() {
                if (page.hasNext()) {
                    lastScoreDoc = page.next();
                    try {
                        Document doc = searcher.doc(lastScoreDoc.doc);
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
//                Stopwatch sw = Stopwatch.createStarted();
                TopDocs topDocs;
                try {
                    if (lastScoreDoc == null) {
                        topDocs = searcher.search(searchQuery, pageSize);
                    } else {
                        topDocs = searcher.searchAfter(lastScoreDoc, searchQuery, pageSize);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
//                System.err.printf("### lucene search: %s, hits: %,d ###\n", sw.stop(), topDocs.scoreDocs.length);

                ScoreDoc[] scoreDocs = topDocs.scoreDocs;
                if (scoreDocs == null || scoreDocs.length == 0) {
                    return Collections.emptyIterator();
                }
                return Arrays.asList(scoreDocs).iterator();
            }
        };

        Stream<String> idStream = Streams.stream(pagingIterator);
//        
//        Stream<String> idStream = Arrays.stream(scoreDocs).map(scoreDoc -> {
//            try {
//                return searcher.doc(scoreDoc.doc);
//            } catch (IOException e) {
//                e.printStackTrace();
//                throw new RuntimeException(e);
//            }
//        }).map(doc -> doc.get("id"));
        idStream = idStream.onClose(() -> {
            try {
                System.err.println("### Closing Lucene IndexReader");
                indexReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return idStream;
//        for (ScoreDoc scoreDoc : scoreDocs) {
//            Document doc = searcher.doc(scoreDoc.doc);
//            infoIds.add(doc.get("id"));
//        }
//        return infoIds;
    }

    private BooleanQuery buildQuery(Class<? extends CatalogInfo> clazz, Iterable<String> terms) {
        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        if (!CatalogInfo.class.equals(clazz)) {// all
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

    private final ConcurrentHashMap<Class<? extends CatalogInfo>, String> storedTypeNames = new ConcurrentHashMap<>();

    private Document createDocument(CatalogInfo info) {
        Set<String> propertyNames = CatalogPropertyAccessor.fullTextProperties(info);
        Document doc = new Document();
        // Add the id as a non indexed property
        String typeFieldValue = typesOf(info);

        StringBuilder anyText = new StringBuilder();
        propertyNames.forEach(p -> {
            Object value = propertyExtractor.getProperty(info, p);
            if (value != null) {
                if (anyText.length() > 0) {
                    anyText.append(' ');
                }
                anyText.append(value);
            }
        });
        String allText = anyText.toString();

        // id stored with the index
        StringField idField = new StringField("id", info.getId(), Store.YES);
        // type stored with the index
        TextField typeField = new TextField("type", typeFieldValue, Store.YES);
        // anyText not stored with the index, it's the big CLOB we're tokenizing and
        // searching for
        TextField anyTextField = new TextField(Predicates.ANY_TEXT.getPropertyName(), allText, Store.NO);

        doc.add(idField);
        doc.add(typeField);
        doc.add(anyTextField);
        return doc;
    }

    private String typesOf(CatalogInfo info) {
        return typesOf(ModificationProxy.unwrap(info).getClass());
    }

    private String typesOf(Class<? extends CatalogInfo> type) {
        return storedTypeNames.computeIfAbsent(type, clazz -> {
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

    public void remove(String id) throws IOException {
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
        checkAlive();
        worker.run(Command.update(createDocument(info)));
    }

    public void doUpdate(Document doc) throws IOException {
        indexWriter.addDocument(doc);// TODO improve to update required fields only?
    }

    private void checkAlive() {
        if (!alive.get()) {
            throw new IllegalStateException("Catalog full text index is not running");
        }
    }

}
