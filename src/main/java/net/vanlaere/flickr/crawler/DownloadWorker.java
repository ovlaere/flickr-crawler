package net.vanlaere.flickr.crawler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.vanlaere.flickr.crawler.datatypes.IntervalResult;
import org.apache.xmlrpc.client.XmlRpcClient;


/**
 *
 * This class provides a client for the XML_RPC API interface of Flickr.
 *
 * For more information:
 * @see http://www.flickr.com/services/api/response.xmlrpc.html
 * @see http://www.flickr.com/services/api/flickr.photos.search.html
 *
 * @author oliviervanlaere@gmail.com
 *
 */
public class DownloadWorker{

    /**
     * Holds a reference to the super crawler process.
     */
    private Crawler crawler;

    /**
     * Holds our XML RPC client.
     */
    private XmlRpcClient [] clients = null;

    /**
     * Field holding the place we are using. This field is completed
     * during construction.
     */
    private IntervalResult ir;

    private String resultDir;

    /**
     * When calling this constructor, 2 arguments should be provided:
     * @param min_upload_date The minimum date used in the search
     * @param max_upload_date The maximum date used in the search
     */
    public DownloadWorker(Crawler crawler, XmlRpcClient[] clients, IntervalResult ir, String resultDir) {
        this.crawler = crawler;
        this.ir = ir;
        this.resultDir = resultDir;
        this.clients = clients;
    }

    /**
     * Implementation of the run method for threading.
     */
    public void download() {
        System.out.println(ir);
        List<Thread> threads = new ArrayList<Thread>();
        for (int pageNumber = 1; pageNumber <= this.ir.getTotalPages(); pageNumber++) {
            String pageString = "" + pageNumber;
            if (pageNumber < 10)
                pageString = "0" + pageNumber;
            String filename = Tools.applyTemplateValues(crawler.DATAFILE_TEMPLATE,
                    new String[]{""+this.ir.getMaxDate(), ""+pageString});
            final File file = new File(resultDir+"/"+filename);
            final boolean newItem = !file.exists() || file.length() < 100;
            if (newItem) {
                final Hashtable<String,Object> struct = crawler.getParameterStructVideoCrawl(
                        this.ir.getMinDate(), this.ir.getMaxDate(), true, pageNumber);
                final XmlRpcClient c = clients[pageNumber-1];
                threads.add(new Thread(new Runnable() {
                    public void run() {
                        String response = crawler.make_call(c, struct);
                        if (response != null) {
                            saveResult(response, file);
                            crawler.requestDownloaded(newItem);
                        }
                        else
                            crawler.requestDownloaded(false);
                    }
                }));
                threads.get(threads.size()-1).start();
                try {
                    Thread.sleep(crawler.MIN_INTER_REQUEST_TIME);
                } catch (InterruptedException ex) {
                    System.err.println("Thread was interrrupted. " + ex.getMessage());
                }
            }
            else
               crawler.requestDownloaded(newItem);
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(DownloadWorker.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Helper method to store the result into a file.
     * @param result Result that should be written to file.
     * @param dir The name of the directory for the output.
     * @param filename Filename of the file containing the results.
     */
    private void saveResult (String result, File file){
        // Prepare the printwriter
        PrintWriter out = null;
        try{
            out = new PrintWriter(new FileWriter(file));
            out.println(result);
            out.close();
        }
        catch (IOException e){
            System.err.println("IO Error: " + e.getMessage());
        }
        finally{
            if (out != null)
                out.close();
        }
    }
}
