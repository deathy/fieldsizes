package ro.deathy.fieldsizes;

import java.io.File;
import java.io.IOException;

public class Runner {

    public static void main(String[] args) throws IOException {
        long startTime = System.currentTimeMillis();
        /*
        Parameters:
            - "luceneIndexDir"
         */
        if (args.length < 1) {
            System.err.println("Missing parameter \"luceneIndexDir\"");
            System.err.println("ro.deathy.fieldsizes.Runner <luceneIndexDir> [statsFile]");
            System.exit(1);
        }
        File luceneIndexDir = new File(args[0]);
        if (!luceneIndexDir.exists() || !luceneIndexDir.isDirectory()) {
            System.err.println("Lucene index dir doesn't exist or is not a directory.");
            System.exit(1);
        }
        FieldSizes fieldSizes;
        if (args.length == 2) {
            File statsFile = new File(args[1]);
            fieldSizes = new FieldSizes(luceneIndexDir, statsFile);
        } else {
            fieldSizes = new FieldSizes(luceneIndexDir);
        }
        fieldSizes.run();
        long endTime = System.currentTimeMillis();
        System.out.println("Total time: " + (endTime - startTime) + " ms.");
    }

}