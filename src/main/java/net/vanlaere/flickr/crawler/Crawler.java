package net.vanlaere.flickr.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.vanlaere.flickr.crawler.datatypes.IntervalResult;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * This class provides a client for the XML_RPC API interface of Flickr.
 * 
 * Please note that the comments are still missing, as this code
 * has been uploaded on request for someone, while I did not have the time
 * yet to comment it properly.
 *
 * For more information:
 * @see http://www.flickr.com/services/api/response.xmlrpc.html
 * @see http://www.flickr.com/services/api/flickr.photos.search.html
 *
 * @author oliviervanlaere@gmail.com
 */
public class Crawler {

    /**
     * Holds your Flickr API Key
     */
    protected String api_key = null;
    
    /**
     * Set the API key in the crawler.
     * @param key Actual API key to use. 
     */
    public void setApiKey(String key) {
        this.api_key = key;
    }
    
    /**
     * Converts a given unix timestamp to a readable date.
     * @param timestamp The timestamp to convert
     * @return A Date object
     */
    public static Date unix2date(long timestamp) {
        return new java.util.Date(timestamp*1000);
    }
    
    /**
     * Holds the URL of the Flickr XML_RPC endpoint.
     */
    protected final String SERVICE_URL = "http://api.flickr.com/services/xmlrpc/";

    /**
     * Minimum accuracy to request for items you want to retrieve from Flickr.
     */
    protected final String MIN_ACCURACY = "1";

    /**
     * Template for storing result files.
     */
    protected final String DATAFILE_TEMPLATE = "response_@1_page_@2.xml";

    /**
     * Filename of the a tmp file that keeps track of the last interval that is being crawled.
     */
    private static final String LAST_INTERVAL_FILE = "lastInterval.tmp";

    /**
     * Setting that keeps track of the maximum number of files per directory with results.
     * Exceeding this threshold might result in a hard to handle file structure. (I had
     * trouble on Linux with values higher than 10K.)
     */
    private static final int MAX_FILES_PER_DIR = 10000;

    /**
     * Minimum time between two requests. This is a global setting: the crawler will
     * not contact the API sooner than this time.
     */
    protected long MIN_INTER_REQUEST_TIME = 2500;

    /**
     * Basic time-out used when a request fails. The system will retry the call with
     * incrementing sleeps, but the basic one starts here.
     */
    private static final int RETRY_BASIC_SLEEP = 16000;

    /*
     * Field holding a global variable that contains a sleep value that will be
     * doubled until the failed request succeeds
     */
    private int retry_current_sleep = RETRY_BASIC_SLEEP;

    /**
     * Number of times to retry when a request fails.
     */
    private static final int MAX_NUMBER_OF_RETRIES = 3;

    /*
     * Variable that counts the total number of succesful remote calls.
     */
    private static int remote_calls_succeeded = 0;

    /*
     * Variable that counts the total number of failed remote calls.
     */
    private static int remote_calls_failed = 0;

    /**
     * Here comes the method name of the API function you would like to call.
     * E.g. "flickr.photos.search".
     */
    private static final String methodName = "flickr.photos.search";

    /**
     * Holds our XML RPC client.
     */
    private XmlRpcClient [] clients = null;

    /**
     * Variable used for tracking the number of results that are found
     * during the current run.
     */
    private int resultsFound = 0;

    /**
     * Set the number of results found in the current run.
     * @param resultsFound The actual number of results found.
     */
    public void setResultsFound(int resultsFound) {
        this.resultsFound = resultsFound;
    }

    /**
     * Field holding the minimum upload date for filtering.
     */
    private long min_upload_date = (long)((new Date()).getTime()/1000)-1;

    /**
     * Set the minimum upload date for time based filtering.
     * @param min_upload_date Unix timestamp for min time
     */
    public void setMin_upload_date(long min_upload_date) {
        this.min_upload_date = min_upload_date;
    }

    /**
     * Field holding the maximum upload date for filtering.
     */
    private long max_upload_date = (long)((new Date()).getTime()/1000);

    /**
     * Set the maximum upload date for time based filtering.
     * @param max_upload_date Unix timestamp for max time
     */
    public void setMax_upload_date(long max_upload_date) {
        this.max_upload_date = max_upload_date;
    }

    /**
     * The initial time interval used to jump into the past. The crawler
     * will start from now and run to the past until it hits your specified
     * end time. It will try to identify intervals that contain at most 4000
     * results. Finding these intervals used this value to jump: -1 interval, 
     * -2 intervals, ... until it exceeds. After that it will run back to the future
     * in smaller steps to identify an interval.
     */
    private final long initial_initial_interval = 3600;

    /**
     * The current interval being used for adaptive detection of intervals.
     */
    private long initial_interval = initial_initial_interval;

    /**
     * Threshold for the number of results to accept in an interval. If an interval
     * is found that exceeds this threshold, it is written to file.
     */
    private static final int ACCEPT_THRESHOLD = 3000;

    /**
     * Holds a queued with results to process in a multithreaded way.
     */
    private List<IntervalResult> queue = new ArrayList<>();
    
    /**
     * Keeps track of the total number of pages to be crawled in the download mode.
     */
    private int totalRequestsToBeDownloaded;

    /**
     * Keeps track of the number of pages that have been downloaded in the current
     * download mode.
     */
    private int requestsDownloadedSoFar = 0;

    /**
     * Keep track of the number of items downloaded in this crawl, and 
     * notifies to the screen.
     * @param notify If True, then "->" will be added to indicate the the request 
     * was skipped due to an error.
     */
    public synchronized void requestDownloaded(boolean notify) {
        this.requestsDownloadedSoFar++;
        if (notify)
            System.out.println("["+requestsDownloadedSoFar + "/" + totalRequestsToBeDownloaded +"]");
        else
            System.out.println("["+requestsDownloadedSoFar + "/" + totalRequestsToBeDownloaded +"] -> ");
    }

    // Keep track of the end date for this crawler
    private long end_date = 0;
    
    /**
     * Set the end date for this crawl.
     * @param end_date Unix timestamp
     */
    public void setEndDate(long end_date) {
        System.out.println("Crawler will run until it hits " + end_date + "\t(" + unix2date(end_date) + ")");
        this.end_date = end_date;
    }

    /**
     * This constructor will create a XML RPC client and
     * configure some parameters.
     */
    public Crawler() {
        try {
            // Create a XML RPC Client config
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            URL service_endpoint = new URL(SERVICE_URL);
            // Set the service endpoint in the config
            config.setServerURL(service_endpoint);
            // Create 16 clients for parallel processing
            this.clients = new XmlRpcClient[16]; // 16 pages max
            for (int i = 0; i < clients.length; i++) {
                // Create an instance of the XML RPC client
                this.clients[i] = new XmlRpcClient();
                // Configure the client
                this.clients[i].setConfig(config);
            }
        }
        catch (MalformedURLException e) {
            System.err.println("Malformed URL: " + e.getMessage());
        }
    }

    /**
     * This method makes a call to the API given the predefined parameters.
     *
     * @param queryAllDetails If true, the API will be queried for detailed info.
     * @return Returns a String containing the response from the server.
     */
    public String call_service(boolean queryAllDetails) {
        // Create a Map that keeps the parameters and their values
        Map<String,Object> parameterMap = getParameters(this.min_upload_date, this.max_upload_date, queryAllDetails, 1);
        // Send the request andResult that should be written to file receive the response
        return make_call(this.clients[0], parameterMap);
    }

    protected Map<String,Object> getParameters(long min_date, long max_date, boolean queryAllDetails, int pageNumber) {
        Map<String,Object> parameterMap = new HashMap<String,Object>();
        // Set the API key
        parameterMap.put("api_key", api_key);
        // Limit the results to images with at least region level accuracy on the location
        parameterMap.put("accuracy", MIN_ACCURACY);
        // Limit the results to images with geo information
        parameterMap.put("has_geo", "1");
        // Limi the results to only photos
        parameterMap.put("media", "photos");
        // Use minimum and maximum upload date to define a bounding box which will result
        // in less or equal of 4000 results (more will not work due to a Flickr bug)
        // Set the minimum value of the upload date
        parameterMap.put("min_upload_date","" + min_date);
        // Set the maximum value of the upload date
        parameterMap.put("max_upload_date","" + max_date);
        // If all detailed are required, query for these extra values
        if (queryAllDetails)
            parameterMap.put("extras","description,license,date_upload,date_taken," +
                    "owner_name,last_update,geo,tags,machine_tags,views," +
                    "media,path_alias,url_o");
        // Possible values
//        description, license, date_upload, date_taken, owner_name, icon_server, original_format, last_update, geo,
//        tags, machine_tags, o_dims, views, media, path_alias, url_sq, url_t, url_s, url_m, url_z, url_l, url_o
        // Number of results per page (default is 250, cannot go higher in API calls)
        parameterMap.put("per_page","250");
        // Select the page in the results we would like to retrieve.
        parameterMap.put("page", pageNumber);
        return parameterMap;
    }

    /**
     * This method makes a call to the API given the predefined parameters.
     *
     * @param client XML-RPC client for making requests
     * @param parameters Map containing the parameters for this request
     * @return Returns an XML String containing the response from the server.
     */
    public String make_call(XmlRpcClient client, Map<String,Object> parameters) {
        // Create a List that will contain the parameters in a map
        List<Map<String,Object>> params = new ArrayList<Map<String, Object>>();
        // Add the parameters according to the struct that was defined before
        params.add(parameters);
        // Send the request andResult that should be written to file receive the response
        String result;
        try{
            result = (String) client.execute(methodName, params);
            try {
                Thread.sleep(MIN_INTER_REQUEST_TIME);
            }
            catch (InterruptedException ex) {
                System.err.println("Interrupted while making RPC call" + ex.getMessage());
            }
        } catch (XmlRpcException e) {
            System.err.println("XML RPC Error: " + e.getMessage());
            result = null;
        }
        // Failed call
        if (result == null || result.length() < 100 || getNumberOfResults(result) < 0) {
            remote_calls_failed++;
            System.out.println("XML RPC Error - ignoring result");
            return null;
        }
        // Call succeeded
        else {
            remote_calls_succeeded++;
            try {
                Thread.sleep(MIN_INTER_REQUEST_TIME);
            } catch (InterruptedException ex) {
                System.err.println("Thread was interrrupted. " + ex.getMessage());
            }
            // In case the request was succesful and the retry sleep was
            // consumed, reset it
            if (retry_current_sleep > RETRY_BASIC_SLEEP)
                retry_current_sleep = RETRY_BASIC_SLEEP;
            // Return the result
            return result;
        }
    }

    /**
     * This method will scan for the intervals.
     *
     * As long as we have not reached the timestamp of the start of the crawl,
     * we will crawl Flickr in intervals of just less than 4000 pictures.
     * 
     * @param outputFile Filename to which the intervals are written.
     */
    public void identifyIntervals(String outputFile){
        try {
            PrintWriter out = new PrintWriter(new FileWriter(outputFile, true), true);
            long start = System.currentTimeMillis();
            while (this.min_upload_date > this.end_date){
                // Find a good interval limiter to get the last 4000 pictures
                // and store it as an IntervalResult
                IntervalResult ir = findTimeInterval();
                // Add the new interval to the queue
                queue.add(ir);
                System.out.println(ir);
                out.println(ir);
                // Prepare the date limits for a new call to findTimeInterval.
                this.max_upload_date = this.min_upload_date-1 ;
                this.min_upload_date = this.max_upload_date-1;
            }
            long stop = System.currentTimeMillis();
            System.out.println("The (partial) crawl took "+(stop-start)+" ms.");
            System.out.println("Call info: ++ SUCCESS ++ : "+remote_calls_succeeded+" | -- FAILED -- : " + remote_calls_failed);
            out.close();
        } catch(IOException e) {
            System.err.println("IOException e:" + e);
            System.exit(1);
        }
    }

    /**
     * This method will try to find a good lower date limit, given the current
     * minimum and maximum date limits. It will lower the min date until the
     * number of results is over 4000, after which it will increase again to
     * find the closest match under 4000.
     * @param min_date Lower date limit to be used in the search
     * @param max_date Upper date limit to be used in the search
     * @return New value for the minumum date, given the provided maximum date
     * to achieve a result set of just under 4000 pictures.
     */
    private IntervalResult findTimeInterval(){

        long initial_min_upload_date = min_upload_date;
        long initial_max_upload_date = max_upload_date;
        int calls = 0;

        Map<Integer, Integer> history = new HashMap<Integer, Integer>();
        int numberOfResults = 0;
        int numberOfPages = 0;
        long interval = initial_interval;
        boolean skip = false;
        do {
            skip = false;
            do {
                this.min_upload_date = Math.max(min_upload_date-(long)(0.5*interval), 0);

                // Perform the request
                String response = call_service(false);
                calls++;
                // Check the number of results
                numberOfResults = getNumberOfResults(response);

                if (history.containsKey(numberOfResults))
                    history.put(numberOfResults, history.get(numberOfResults) + 1);
                else
                    history.put(numberOfResults, 1);

                int retries = 0;
                while (numberOfResults == 0 && retries++ < MAX_NUMBER_OF_RETRIES) {
                    // If we use pauzes, then do it here
                    try{
                        Thread.sleep(1000 * retries * retries);
                    }
                    catch (InterruptedException e){
                        System.err.println("Thread Interrupted: " + e.getMessage());
                    }
                    response = call_service(false);
                    calls++;
                    numberOfResults = getNumberOfResults(response);
                    System.out.println(numberOfResults + "\t  \t["+this.min_upload_date+","+this.max_upload_date+"] RETRY " + retries + "(sleep "+(retries*retries)+")");

                    if (history.containsKey(numberOfResults))
                        history.put(numberOfResults, history.get(numberOfResults) + 1);
                    else
                        history.put(numberOfResults, 1);
                }
                // Get the number of pages
                numberOfPages = getNumberOfPages(response);
                System.out.println(numberOfResults + "\t<<\t["+this.min_upload_date+", "+this.max_upload_date+"]\t"
                        + "["+unix2date(this.min_upload_date)+", "+unix2date(this.max_upload_date)+"]");
                // skip this time interval as there seems to be something wrong?
                if (numberOfResults == 0) {
                    skip = true;
                }
            }
            // Do this wil the number of results is < 4000 AND min_date > 0
            while ((numberOfResults < ACCEPT_THRESHOLD && this.min_upload_date > 0 && !skip));

            long interval_back = interval / 5;

            while (numberOfResults > 4000) {
                this.min_upload_date += interval_back;

                if (this.min_upload_date == this.max_upload_date) {
                    initial_interval /= 2;
                    numberOfResults = 0;
                }
                else {
                    // Get the result for the last interval
                    String response = call_service(false);
                    calls++;
                    // Find out the number of results
                    numberOfResults = getNumberOfResults(response);

                    if (history.containsKey(numberOfResults))
                        history.put(numberOfResults, history.get(numberOfResults) + 1);
                    else
                        history.put(numberOfResults, 1);

                    int retries = 0;
                    while (numberOfResults == 0 && retries++ < MAX_NUMBER_OF_RETRIES) {
                        // If we use pauzes, then do it here
                        try{
                            Thread.sleep(1000 * retries * retries);
                        }
                        catch (InterruptedException e){
                            System.err.println("Thread Interrupted: " + e.getMessage());
                        }
                        response = call_service(false);
                        calls++;
                        numberOfResults = getNumberOfResults(response);
                        System.out.println(numberOfResults + "\t  \t["+this.min_upload_date+","+this.max_upload_date+"] RETRY " + retries + "(sleep "+(retries*retries)+")");

                        if (history.containsKey(numberOfResults))
                            history.put(numberOfResults, history.get(numberOfResults) + 1);
                        else
                            history.put(numberOfResults, 1);
                    }
                    // Get the number of pages
                    numberOfPages = getNumberOfPages(response);
                    System.out.println(numberOfResults + "\t>>\t["+this.min_upload_date+", "+this.max_upload_date+"]\t"
                            + "["+unix2date(this.min_upload_date)+", "+unix2date(this.max_upload_date)+"]");
                }
            }

            if (this.min_upload_date < 0){
                // Set the min date to 0
                this.min_upload_date = 0;
                // Get the result for the last interval
                String response = call_service(false);
                // Find out the number of results
                numberOfResults = getNumberOfResults(response);
                // Get the number of pages
                numberOfPages = getNumberOfPages(response);
            }

            // Reset this interval search
            if (calls >= 20) {
                for (Integer value : history.values()) {
                    if (value >= 5) {
                        this.min_upload_date = initial_min_upload_date;
                        this.max_upload_date = initial_max_upload_date;
                        break;
                    }
                }
            }
        }
        while (numberOfResults == 0 && this.min_upload_date > 0 && !skip);

        this.initial_interval = (long)((this.max_upload_date - this.min_upload_date));

        if (!skip) {
            // Add the number of results to the total counter
            this.resultsFound += numberOfResults;
            System.out.println(" ** Results so far: "+ this.resultsFound + " **");
            // Return a new IntervalResult containing the results
            return new IntervalResult(this.min_upload_date,this.max_upload_date,numberOfPages,numberOfResults);
        }
         else {
            System.out.println(" >> Skipped interval due to no results/timeout/error <<");
            // Return a new IntervalResult containing the results
            this.initial_interval = initial_initial_interval;
            return new IntervalResult(this.min_upload_date,this.max_upload_date,0,0);
         }
    }

    /**
     * Download the data.
     * @param resultsDir Directory containing the results.
     */
    public void downloadData(String resultsDir) {
        // Fetch all current directories
        File dir = new File(resultsDir);
        File [] files = dir.listFiles();
        int dircounter = 1;
        if (files.length > 0) {
            List<String> dirnames = new ArrayList<String>();
            for (File file : files) {
                if (file.isDirectory())
                    dirnames.add(file.getName());
            }
            Collections.sort(dirnames);
            String dirname = dirnames.get(dirnames.size()-1);
            // Get the number of the most recent dir
            dircounter = Integer.parseInt(dirname.substring(dirname.indexOf("_")+1));
        }
        String dircounterString = "" + dircounter;
        // Prefix zeros
        while (dircounterString.length() < 3)
            dircounterString = "0" + dircounterString;

        String dirname = resultsDir + "chunk_" + dircounterString;
        System.out.println("Using output dir: " + dirname);
        File outputDir = new File(dirname);

        // If the directory reached the file limit - switch to a new one
        if (outputDir.exists() && outputDir.listFiles().length > MAX_FILES_PER_DIR) {
            dircounterString = "" + ++dircounter;
            // Prefix zeros
            while (dircounterString.length() < 3)
                dircounterString = "0" + dircounterString;

            dirname = resultsDir + "chunk_" + dircounterString;
            outputDir = new File(dirname);
            System.out.println("Switching output dir to: " + outputDir);
        }

        // For all intervals we have on queue
        for (IntervalResult ir : queue) {
            // Create the directory if it does not exist
            if (!outputDir.exists())
                outputDir.mkdirs();
            // Start a new DownloadWorker
            new DownloadWorker(this, this.clients, ir, outputDir.toString()).download();
            // Write the last interval we processed to file
            try {
                PrintWriter writer = new PrintWriter(new FileWriter(LAST_INTERVAL_FILE, false));
                writer.println(ir.toString());
                writer.close();
            }
            catch (IOException e) {
                System.err.println("Error writing the last downloaded interval to file!");
            }

            // If the directory reached the file limit - switch to a new one
            if (outputDir.listFiles().length > MAX_FILES_PER_DIR) {
                dircounterString = "" + ++dircounter;
                // Prefix zeros
                while (dircounterString.length() < 3)
                    dircounterString = "0" + dircounterString;

                dirname = resultsDir + "chunk_" + dircounterString;
                outputDir = new File(dirname);
                System.out.println("Switching output dir to: " + outputDir);
            }
        }
    }

    /**
     * Helper method for parsing the the total number of results from a query.
     * @param result XML response from the server.
     */
    private int getNumberOfResults(String result){
        try {
            int startIndex = result.indexOf("total=\"") + "total=\"".length();
            int stopIndex = result.indexOf("\"",startIndex+1);
            return Integer.parseInt(result.substring(startIndex,stopIndex));
        }
        catch(StringIndexOutOfBoundsException ex) {
            System.err.println("Error parsing the number of results. Did we get kicked?");
            System.err.println(result);
            return -1;
        }
    }

    /**
     * Helper method for parsing the the number of pages for the results from a query.
     * @param result XML response from the server.
     */
    private int getNumberOfPages(String result){
        int startIndex = result.indexOf("pages=\"") + "pages=\"".length();
        int stopIndex = result.indexOf("\"",startIndex+1);
        return Integer.parseInt(result.substring(startIndex,stopIndex));
    }

    /**
     * This method configures JAVA for using a proxy server.
     * @param host Proxy host
     * @param port Proxy port
     */
    public static void setUpProxy(String host, String port) {
        System.out.println("Setting up proxy... ");
        System.getProperties().put("proxySet", "true");
        System.getProperties().put("proxyHost", host);
        System.getProperties().put("proxyPort", port);
        System.out.println("done.");
    }

    /**
     * Load the intervals that are already on file.
     * @param filename The file containing the intervals.
     * @return 0 if everything was ok, -1 if there was an error
     */
    public int loadIntervalsFromFile(String filename) {
        this.queue = new ArrayList<IntervalResult>();
        // Try to load the last processed interval from file
        File lastIntervalProcessed = new File(LAST_INTERVAL_FILE);
        IntervalResult lastInterval = null;
        if (lastIntervalProcessed.exists()) {
            try {
                BufferedReader file = new BufferedReader(new FileReader(lastIntervalProcessed));
                String line = file.readLine();
                String [] values = line.split(" ");
                int min_date = Integer.parseInt(values[1]);
                int max_date = Integer.parseInt(values[3]);
                int totalPages = Integer.parseInt(values[10]);
                int numberOfResults = Integer.parseInt(values[7]);
                lastInterval = new IntervalResult(min_date, max_date, totalPages, numberOfResults);
            }
            catch (IOException e) {
                System.err.println("Error reading last interval from file!");
            }
        }
        // Load the data from the interval file
        BufferedReader file = null;
        int skipped = 0;
        int results = 0;
        String line;
        // Assume we WILL skip info until we see the last interval
        boolean stop_skipping = (lastInterval == null ? true : false);
        try {
            file = new BufferedReader(new FileReader(filename));
            line = file.readLine();
            while (line != null) {
                // Parse the data
                String [] values = line.split(" ");
                int min_date = Integer.parseInt(values[1]);
                int max_date = Integer.parseInt(values[3]);
                int totalPages = Integer.parseInt(values[10]);
                int numberOfResults = Integer.parseInt(values[7]);
                IntervalResult current = new IntervalResult(min_date, max_date, totalPages, numberOfResults);
                // As long as we havent seen the last interval, we keep skipping
                if (stop_skipping && !(numberOfResults == 0 && totalPages == 0)) {
                    this.totalRequestsToBeDownloaded += totalPages;
                    results += numberOfResults;
                    queue.add(current);
                }
                // If this is not yet the last interval we crawled, keep skipping
                else {
                    skipped++;
                }
                // If we found the last interval crawled
                if (current.equals(lastInterval)) {
                    // Stop skipping data
                    stop_skipping = true;
                    System.out.println("Skipped up to "+ current +", "+ skipped +" intervals.");
                }
                line = file.readLine();
            }
            // Print some info
            System.out.println(results + "\t" + this.totalRequestsToBeDownloaded + "\t" + filename);
        } catch (IOException ex) {
            System.err.println("IOException: "+ ex.getMessage());
            return -1;
        } finally {
            try {
                if (file != null)
                    file.close();
            } catch (IOException ex) {
                System.err.println("Error closing the BufferedReader. " + ex.getMessage());
            }
        }
        return 0;
    }

    /**
     * Main method.
     * 
     * @param args
     *  Expected arguments: interval|download timestamp_end intervalfile data_dir
     */
    public static void main(String[] args) {
        // Check for valid parameters
        if (args.length == 0 || (args.length !=5 && args.length != 7)) {
            System.out.println("Missing arguments.");
            System.out.println("Usage: api_key <scan|download> timestamp_end intervalfile data_dir [proxyHost proxyPort]");
            System.out.println(" api_key            \tSpecify your Flickr API key");
            System.out.println(" <scan|download>    \tSpecify the command to either scan for intervals or download data");
            System.out.println(" timestamp_end      \tSpecify the (UNIX) timestamp at which point the crawler should stop");
            System.out.println(" intervalfile       \tFile containing the intervals that are already discovered");
            System.out.println(" data_dir           \tThe directory where the downloaded raw XML data will go");
            System.out.println(" ProxyHost and port \t(Optional) Specify for using a proxy");
            System.exit(0);
        }
        // Parse commands
        String api_key = args[0]; //"f8b52a66d4d5fa5c40b07ef6aaa2251b";
        String command = args[1];
        long end_date = Long.parseLong(args[2]);
        String intervalfile = args[3];
        String data_dir = args[4];
        // If there is a need for a proxy, set it here
        if (args.length == 7) {
            setUpProxy(args[5], args[6]);
        }

        // Init the crawler instance
        Crawler crawler = new Crawler();
        crawler.setApiKey(api_key);
        crawler.setEndDate(end_date);
        
        // Determine action
        switch(command) {
            case "scan":
                // Check for the interval file
                File test = new File(intervalfile);
                // If the file already exists
                if (test.exists()) {
                    try {
                        // Load the intervals indentified so far
                        BufferedReader file = new BufferedReader(new FileReader(intervalfile));
                        String line = file.readLine();
                        String previousLine = line;
                        // First count the number of results we already have
                        int results = 0;
                        while (line != null) {
                            previousLine = line;
                            String [] values = line.split(" ");
                            results += Integer.parseInt(values[7]);
                            line = file.readLine();
                        }
                        file.close();
                        // Using the last line we saw, parse the time interval to start from
                        if (previousLine != null) {
                            String [] values = previousLine.split(" ");
                            int min_date = Integer.parseInt(values[1]);
                            // Set min and max upload date different to start with
                            crawler.setMax_upload_date(min_date - 1);
                            crawler.setMin_upload_date(min_date - 2);
                            // Set the results found so far
                            crawler.setResultsFound(results);
                            System.out.println("Resuming from " + min_date + "\t("+ unix2date(min_date) +")");
                        }
                    } catch (IOException e) {
                        System.err.println("IOException: " + e);
                    }
                }
                // The file did not exist at this point
                
                // if the intervalfile has a path in between
                if (intervalfile.contains(File.separator)) {
                    String path = intervalfile.substring(0, intervalfile.lastIndexOf("/"));
                    // Make the directories in this path
                    new File(path).mkdirs();
                }
                // Start or continue crawling the intervals
                crawler.identifyIntervals(intervalfile);
                
                break;
            
            // Download given a list of existing intervals
            case "download":
                int errorCode = crawler.loadIntervalsFromFile(intervalfile);
                // If no error occurs loading this data
                if (errorCode == 0) {
                    // Check if the directory exists
                    File dir = new File(data_dir+"/");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    // Start/Resume downloading data
                    crawler.downloadData(data_dir+"/");
                }
                
                break;
        }
    }
}