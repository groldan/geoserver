package org.geoserver.catalog.impl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.Predicates;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Streams;

public class CatalogInfoLookupFullTextIndex {

    private final CatalogPropertyAccessor propertyExtractor = new CatalogPropertyAccessor();

    private FSDirectory idx;
    private IndexWriter indexWriter;

    private final Path indexPath;

    public CatalogInfoLookupFullTextIndex(Path indexPath) {
        this.indexPath = indexPath;
        Objects.requireNonNull(indexPath);
    }

    public void open() throws IOException {
        if (idx == null) {
            idx = MMapDirectory.open(indexPath);
            IndexWriterConfig conf = new IndexWriterConfig(new StandardAnalyzer());
            conf.setOpenMode(OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(idx, conf);
        }
    }

    public void close() throws IOException {
        FSDirectory idx = this.idx;
        this.idx = null;
        if (idx != null) {
            idx.close();
        }
    }

    public void add(CatalogInfo info) throws IOException {
        Objects.requireNonNull(info);
        Document document = createDocument(info);
        indexWriter.addDocument(document);
//        indexWriter.commit();
    }

    public void commit() {
        try {
            indexWriter.commit();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public Stream<String> search(String... terms) throws IOException {
        return search(Arrays.asList(terms));
    }

    public Stream<String> search(List<String> terms) throws IOException {
        return search(CatalogInfo.class, terms);
    }

    public Stream<String> search(Class<? extends CatalogInfo> clazz, Iterable<String> terms) throws IOException {

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
        System.err.println("Lucence query: " + query);

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
        // TODO Auto-generated method stub

    }

    public void update(CatalogInfo info) throws IOException {
        // TODO Auto-generated method stub

    }

}
