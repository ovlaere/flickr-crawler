package net.vanlaere.flickr.parse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.processing.FilerException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

public class FlickrXMLParser {

    /**
     * Constant containing the number of processors available in the system.
     */
    private static final int NR_THREADS = Runtime.getRuntime().availableProcessors();

    /**
     * Numberformat.
     */
    protected  static final NumberFormat formatter = new DecimalFormat("#00.00");

    private static final int REPORT_SIZE = 1000;

    private int total_to_process = 0;

    private int total_processed = 0;

    public synchronized void report() {
        total_processed += REPORT_SIZE;
        double percent = total_processed * 100. / total_to_process;
        System.out.println(total_processed + "/" + total_to_process +
                " ("+ formatter.format(percent) +" %)");
    }

    public FlickrXMLParser(String dir, String outputFile) {
        ArrayList<String> filenames = getFileQueue(dir);
        this.total_to_process = filenames.size();
        System.out.println("Total files: " + total_to_process);
        // if the outputfile has a path in between
        if (outputFile.contains(File.separator)) {
            System.out.println(outputFile + " making dir");
            String path = outputFile.substring(0, outputFile.lastIndexOf("/"));
            // Make the directories in this path
            new File(path).mkdirs();
        }
        ExecutorService executor = Executors.newFixedThreadPool(NR_THREADS);
        List<Future<File>> list = new ArrayList<Future<File>>();
        int length = (int) (filenames.size() * 1.0 / NR_THREADS);
        for (int i = 0; i < NR_THREADS; i++) {
            int begin = i * length;
            if (i == NR_THREADS - 1) {
                length = filenames.size() - (i * length);
            }
            int end = begin + length;
            Callable<File> worker = new DataProcessorMultiFileHelper(filenames, begin, end);
            Future<File> submit = executor.submit(worker);
            list.add(submit);
        }
        try {
            OutputStream out = new FileOutputStream(outputFile, true);
            for (Future<File> future : list) {            
                File tmp_file = future.get();
                System.out.println("Processing " + tmp_file.getName());
                // BLOCK COPY
                InputStream in = new FileInputStream(tmp_file);
                // Transfer bytes from in to out
                byte[] buf = new byte[1024*4]; // 4KB blocks
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                tmp_file.delete();
            }
            out.close();
        } catch (FilerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        // This will make the executor accept no new threads
        // and finish all existinkeyg threads in the queue
        executor.shutdown();
        // Wait until all threads are finish
        while (!executor.isTerminated()) {}
        System.out.println("Processed data can be found in " + outputFile);
    }

    private ArrayList<String> getFileQueue(String dir) {
        System.out.println("Scanning directory " + dir);
        ArrayList<String> filenames = new ArrayList<String>();
        File directory = new File(dir);
        File[] files = directory.listFiles();
        Arrays.sort(files);
        for (File file : files) {
            if (file.isDirectory())
                filenames.addAll(getFileQueue(file.getPath()));
            else if (file.getName().endsWith(".xml"))
                filenames.add(file.getAbsolutePath());
        }
        return filenames;
    }

    /**
     * Helper class for multithreaded processing of XML files.
     */
    private class DataProcessorMultiFileHelper implements Callable<File> {

        /**
         * Index of the beginning of the data this thread has to process.
         */
        private int begin;

        /**
         * Index of the end of the data this thread has to process.
         */
        private int end;

        /**
         * The data to process.
         */
        private List<String> data;

        private int processed = 0;

        /**
         * Constructor.
         * @param data Data to process
         * @param begin index of the beginning of the processing
         * @param end index of the end of the processing
         */
        public DataProcessorMultiFileHelper(List<String> data, int begin, int end) {
            this.data = data;
            this.begin = begin;
            this.end = end;
        }

        /**
         * Process the files in the queue.
         * @return a File containing the processed results
         * @throws Exception
         */
        @Override
        public File call() throws Exception {
            File file = null;
            try {
                File tmp_file = File.createTempFile("DataProcessor_", ".tmp");
                String filename = tmp_file.getName();
                file = new File(filename);
                System.out.println(file.getName());
                tmp_file.delete();
                PrintWriter out = new PrintWriter(new FileWriter(file), true);
                for (int i = begin; i < end; i++) {
                    String xmlfile = data.get(i);
                    FlickrXMLParseImpl parser = new FlickrXMLParseImpl(xmlfile, out);
                    if (++processed % REPORT_SIZE == 0) {
                        report();
                    }
                }
                out.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return file;
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Missing arguments.");
            System.out.println("Usage: inputdir outputfile");
        }
        else {
            FlickrXMLParser parser = new FlickrXMLParser(args[0], args[1]);
        }
    }

    private class FlickrXMLParseImpl extends DefaultHandler {

        private PrintWriter file;

        public FlickrXMLParseImpl(String filename, PrintWriter file) {
            this.file = file;
            try {
                XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                xmlReader.setContentHandler(this);
                xmlReader.parse(new InputSource(new FileReader(filename)));
                xmlReader = null;
            } catch (FileNotFoundException e) {
                System.out.println("Error in file " + filename);
            } catch (IOException e) {
                System.out.println("Error in file " + filename);
            } catch (SAXException e) {
                System.out.println("Error in file " + filename);
            }
        }

        @Override
        public void startElement(String uri, String name, String qName,
                        Attributes atts) {

            if (name.equals("photo")) {

                long id = -1L;
                String owner = "";
                String title = "";
                int license = -1;
                String datetaken = "";
                String ownername = "";
                long lastupdate = -1L;
                double latitude = -200.;
                double longitude = -200.;
                int accuracy = -1;
                String place_id = "";
                long woeid = -1L;
                String tags = "";
                String machine_tags = "";
                int views = -1;
                String url_o = "";

                String element;

                element = atts.getValue("id");
                if (element != null)
                    id = Long.parseLong(element);

                element = atts.getValue("owner");
                if (element != null)
                    owner = element;

                element = atts.getValue("title");
                if (element != null)
                    title = element;

                element = atts.getValue("license");
                if (element != null)
                    license = Integer.parseInt(element);

                element = atts.getValue("datetaken");
                if (element != null)
                    datetaken = element;

                element = atts.getValue("ownername");
                if (element != null)
                    ownername = element;

                element = atts.getValue("lastupdate");
                if (element != null)
                    lastupdate = Long.parseLong(element);

                element = atts.getValue("latitude");
                if (element != null)
                    latitude = Double.parseDouble(element);

                element = atts.getValue("longitude");
                if (element != null)
                    longitude = Double.parseDouble(element);

                element = atts.getValue("accuracy");
                if (element != null)
                    accuracy = Integer.parseInt(element);

                element = atts.getValue("place_id");
                if (element != null)
                    place_id = element;

                element = atts.getValue("woeid");
                if (element != null)
                    woeid = Long.parseLong(element);

                element = atts.getValue("tags");
                if (element != null)
                    tags = element;

                element = atts.getValue("machine_tags");
                if (element != null)
                    machine_tags = element;

                element = atts.getValue("views");
                if (element != null)
                    views = Integer.parseInt(element);

                element = atts.getValue("url_o");
                if (element != null)
                    url_o = element;

                // Only process valid geocoords and photos with tags
                if (latitude > -200 && longitude > -200 && !tags.equals("")) {

                    StringBuilder builder = new StringBuilder();
                    builder.append("owner=\"");
                    builder.append(owner);
                    builder.append("\";id=\"");
                    builder.append(id);
                    builder.append("\";title=\"");
                    builder.append(title);
                    builder.append("\";license=\"");
                    builder.append(license);
                    builder.append("\";datetaken=\"");
                    builder.append(datetaken);
                    builder.append("\";ownername=\"");
                    builder.append(ownername);
                    builder.append("\";lastupdate=\"");
                    builder.append(lastupdate);
                    builder.append("\";latitude=\"");
                    builder.append(latitude);
                    builder.append("\";longitude=\"");
                    builder.append(longitude);
                    builder.append("\";accuracy=\"");
                    builder.append(accuracy);
                    builder.append("\";place_id=\"");
                    builder.append(place_id);
                    builder.append("\";woeid=\"");
                    builder.append(woeid);
                    builder.append("\";tags=\"");
                    builder.append(tags);
                    builder.append("\";machine_tags=\"");
                    builder.append(machine_tags);
                    builder.append("\";views=\"");
                    builder.append(views);
                    builder.append("\";url_o=\"");
                    builder.append(url_o);
                    builder.append("\"");

                    file.println(builder.toString());
                }
            }
        }
    }
}