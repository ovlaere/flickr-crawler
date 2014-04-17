package net.vanlaere.flickr.crawler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final Crawler crawler;

    /**
     * Holds our XML RPC client.
     */
    private XmlRpcClient [] clients = null;

    /**
     * Field holding the place we are using. This field is completed
     * during construction.
     */
    private final IntervalResult ir;

    /**
     * Reference to the directory to write the results to.
     */
    private final String resultDir;

    /**
     * File size that might indicate an error in a previous download for a page.
     */
    private final int EMPTY_FILE_INDICATOR = 100;
    
    /**
     * Construct a new DownloadWorker. This will download the actual data for a 
     * specific interval provided at construction time.
     * @param crawler Reference to the Crawler instance.
     * @param clients Reference to the array of XML clients to use in threads
     * @param ir The actual interval to download (defined by an IntervalResult)
     * @param resultDir Directory to store the results in
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
        // Display info on screen about this interval
        System.out.println(ir);
        List<Thread> threads = new ArrayList<Thread>();
        // For each of the pages in the result we are processing
        for (int pageNumber = 1; pageNumber <= this.ir.getTotalPages(); pageNumber++) {
            String pageString = "" + pageNumber;
            if (pageNumber < 10)
                pageString = "0" + pageNumber;
            // Prepare filename for this page
            String filename = Tools.applyTemplateValues(crawler.DATAFILE_TEMPLATE,
                    new String[]{""+this.ir.getMaxDate(), ""+pageString});
            // Check if the file existed on file but was too small
            final File file = new File(resultDir+"/"+filename);
            final boolean newItem = !file.exists() || file.length() < EMPTY_FILE_INDICATOR;
            // If this is an unseen page to download
            if (newItem) {
                // Get the parameters
                final Map<String,Object> parameters = crawler.getParameters(
                        this.ir.getMinDate(), this.ir.getMaxDate(), true, pageNumber);
                // Get the client
                final XmlRpcClient c = clients[pageNumber-1];
                // Start new Thread
                threads.add(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Call the API
                        String response = crawler.make_call(c, parameters);
                        // Check response
                        if (response != null) {
                            // Save the result to file
                            saveResult(response, file);
                            // Indicate that this request succeeded
                            crawler.requestDownloaded(newItem);
                        }
                        // This failed
                        else
                            // Notify that it failed
                            crawler.requestDownloaded(false);
                    }
                }));
                // Start this thread
                threads.get(threads.size()-1).start();
                try {
                    // Wait the inter request time before calling the API again
                    Thread.sleep(crawler.MIN_INTER_REQUEST_TIME);
                } catch (InterruptedException ex) {
                    System.err.println("Thread was interrrupted. " + ex.getMessage());
                }
            }
            // The specific page already existed
            else
                // Just notify that it exists
                crawler.requestDownloaded(newItem);
        }
        // Wait for all threads to finish
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
        try( 
            // Prepare the printwriter
            PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.println(result);
            out.close();
        }
        catch (IOException e){
            System.err.println("IO Error: " + e.getMessage());
        }
    }
}