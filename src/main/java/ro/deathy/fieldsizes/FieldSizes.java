package ro.deathy.fieldsizes;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FieldSizes {

    private static final int reportingDocInterval = 10000;
    private static final Charset charsetForStoredStringFields = Charset.forName("UTF-8");
    
    private final File luceneIndexDir;
    private final File statsFile;

    private IndexReader indexReader;
    private int maxDoc;

    private boolean failedSetup = false;

    private Map<String, AtomicInteger> fieldDocumentAppearances = new HashMap<String, AtomicInteger>();
    private Map<String, AtomicLong> fieldSizesInBytes = new HashMap<String, AtomicLong>();

    public FieldSizes(File luceneIndexDir) {
        this.luceneIndexDir = luceneIndexDir;
        this.statsFile = null;
    }

    public FieldSizes(File luceneIndexDir, File statsFile) {
        this.luceneIndexDir = luceneIndexDir;
        this.statsFile = statsFile;
    }

    private void setUp() {
        try {
            Directory directory = FSDirectory.open(luceneIndexDir);
            IndexReader indexReader = DirectoryReader.open(directory);
            this.maxDoc = indexReader.maxDoc();
            this.indexReader = indexReader;
        } catch (IOException e) {
            System.err.println("Error during initialization.");
            e.printStackTrace();
            failedSetup = true;
        }
    }

    public void run() throws IOException {
        setUp();
        if (failedSetup) {
            System.err.println("Failed initialization. Exiting.");
            System.exit(1);
        }
        
        for (int i = 0; i < maxDoc; i++) {
            Document document = indexReader.document(i);
            List<IndexableField> fields = document.getFields();
            for (IndexableField field : fields) {
                String fieldName = field.name();

                AtomicInteger fieldAppearances = fieldDocumentAppearances.get(fieldName);
                if (fieldAppearances == null) {
                    fieldDocumentAppearances.put(fieldName, new AtomicInteger(1));
                } else {
                    fieldAppearances.incrementAndGet();
                }

                int fieldSizeInBytes = 0;
                if (field.binaryValue()!=null) {
                    fieldSizeInBytes = field.binaryValue().length;
                } else if (field.stringValue()!=null) {
                    String stringValue = field.stringValue();
                    byte[] stringBytes = stringValue.getBytes(charsetForStoredStringFields);
                    fieldSizeInBytes = stringBytes.length;
                }
                AtomicLong fieldSize = fieldSizesInBytes.get(fieldName);
                if (fieldSize == null) {
                    fieldSizesInBytes.put(fieldName, new AtomicLong(fieldSizeInBytes));
                } else {
                    fieldSize.addAndGet(fieldSizeInBytes);
                }

            }
            if (i % reportingDocInterval == 0) {
                System.out.println("Percentage done: " + (((i * 1.0f) / maxDoc) * 100.0f) + "%");
            }
        }

        writeStats();
    }

    private void writeStats() {
        PrintStream printStream;
        if (statsFile == null) {
            printStream = System.out;
        } else {
            try {
                printStream = new PrintStream(statsFile);
            } catch (FileNotFoundException e) {
                System.err.println("Error writing to statsfile. Using System.out");
                printStream = System.out;
            }
        }

        List<SortableNamedEntry> entries = new ArrayList<SortableNamedEntry>();
        long total = 0;
        for(String fieldName: fieldDocumentAppearances.keySet()){
            long current = fieldDocumentAppearances.get(fieldName).longValue();
            entries.add(new SortableNamedEntry(fieldName, current));
            total += current;
        }
        Collections.sort(entries);
        Collections.reverse(entries);

        printStream.println("Field Name\tDocument Appearances\tPercentage");
        printStream.println("Total\t"+maxDoc+"\t"+(((maxDoc*1.0f)/maxDoc)*100.0f));
        for(SortableNamedEntry entry: entries){
            printStream.println(entry.fieldName+"\t"+entry.numericValue+"\t"+(((entry.numericValue*1.0f)/maxDoc)*100.0f));
        }

        entries.clear();
        total = 0;
        for(String fieldName: fieldSizesInBytes.keySet()){
            long current = fieldSizesInBytes.get(fieldName).longValue();
            entries.add(new SortableNamedEntry(fieldName, current));
            total += current;
        }
        Collections.sort(entries);
        Collections.reverse(entries);

        printStream.println("\n\n");

        printStream.println("Field Name\tBytes Used\tPercentage");
        printStream.println("Total\t"+total+"\t"+(((total*1.0f)/total)*100.0f));
        for(SortableNamedEntry entry: entries){
            printStream.println(entry.fieldName+"\t"+entry.numericValue+"\t"+(((entry.numericValue*1.0f)/total)*100.0f));
        }

        printStream.flush();
        printStream.close();
    }

    private final class SortableNamedEntry implements Comparable<SortableNamedEntry> {
        
        private String fieldName;
        private long numericValue;

        private SortableNamedEntry(String fieldName, long numericValue) {
            this.fieldName = fieldName;
            this.numericValue = numericValue;
        }

        public int compareTo(SortableNamedEntry o) {
            return Long.compare(this.numericValue, o.numericValue);
        }

    }

}