package net.vanlaere.flickr.crawler;

import net.vanlaere.flickr.crawler.datatypes.IntervalResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * This class provides a client for the XML_RPC API interface of Flickr.
 *
 * For more information:
 * @see http://www.flickr.com/services/api/response.xmlrpc.html
 * @see http://www.flickr.com/services/api/flickr.photos.search.html
 *
 * @author oliviervanlaere@gmail.com
 */
public class Crawler {

    /**
     * Here comes your Flickr API key.
     */
    protected String api_key = null;
    
    public void setApiKey(String key) {
        this.api_key = key;
    }
    
    /**
     * Holds the URL of the Flickr XML_RPC endpoint.
     */
    protected final String SERVICE_URL = "http://api.flickr.com/services/xmlrpc/";

    protected final String MIN_ACCURACY = "1";

    protected final String DATAFILE_TEMPLATE = "response_@1_page_@2.xml";

    private static final String LAST_INTERVAL_FILE = "lastInterval.tmp";

    private static final int MAX_FILES_PER_DIR = 10000;

    private static final NumberFormat formatter = new DecimalFormat("#0.00");

    protected long MIN_INTER_REQUEST_TIME = 2500;

    private static final int RETRY_BASIC_SLEEP = 16000;

    /*
     * Field holding a global variable that contains a sleep value that will be
     * doubled until the failed request succeeds
     */
    private int retry_current_sleep = RETRY_BASIC_SLEEP;

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

    /*
     * Field holding the total number of results for the current crawl
     */
    private int numberOfTotalResults;

    private int resultsFound = 0;

    public void setResultsFound(int resultsFound) {
        this.resultsFound = resultsFound;
    }

    /**
     * Field holding the minimum upload date for filtering.
     */
    private long min_upload_date = (long)((new Date()).getTime()/1000)-1;

    public void setMin_upload_date(long min_upload_date) {
        this.min_upload_date = min_upload_date;
    }

    private long max_upload_date_start = (long)((new Date()).getTime()/1000);

    public void setMax_upload_date_start(long max_upload_date_start) {
        this.max_upload_date_start = max_upload_date_start;
    }

    /**
     * Field holding the maximum upload date for filtering.
     */
    private long max_upload_date = (long)((new Date()).getTime()/1000);

    public void setMax_upload_date(long max_upload_date) {
        this.max_upload_date = max_upload_date;
    }

    private final long initial_initial_interval = 3600;

    private long initial_interval = initial_initial_interval;

    private static final int ACCEPT_THRESHOLD = 3000;

    /**
     * Holds a queued with results to process in a multithreaded way.
     */
    private List<IntervalResult> queue = new ArrayList<>();

    private int totalRequestsToBeDownloaded;

    private int requestsDownloadedSoFar = 0;

    public synchronized void requestDownloaded(boolean notify) {
        this.requestsDownloadedSoFar++;
        if (notify)
            System.out.println("["+requestsDownloadedSoFar + "/" + totalRequestsToBeDownloaded +"]");
        else
            System.out.println("["+requestsDownloadedSoFar + "/" + totalRequestsToBeDownloaded +"] -> ");
    }

    // Keep track of the end date for this crawler
    private long end_date = 0;
    
    public void setEndDate(long end_date) {
        System.out.println("Crawler will run until it hits " + end_date);
        this.end_date = end_date;
    }
//    private long end_date = 1303492225; // 1 aug 2010

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
     * @return Returns a String containing the response from the server.
     */
    public String call_service(boolean queryAllDetails) {
        // Create a Vector that will contain the parameters
        Vector<Hashtable<String,Object>> params = new Vector<Hashtable<String,Object>>();

        // Create a Hashtable that keeps the parameters and their values
        Hashtable<String,Object> struct = getParameterStructVideoCrawl(this.min_upload_date, this.max_upload_date, queryAllDetails, 1);

        // Add the parameters according to the struct that was defined before
        params.addElement(struct);
        // Send the request andResult that should be written to file receive the response
        return make_call(this.clients[0], struct);
    }

    protected Hashtable<String,Object> getParameterStructVideoCrawl(long min_date, long max_date, boolean queryAllDetails, int pageNumber) {
        Hashtable<String,Object> struct = new Hashtable<String,Object>();
        // Set the API key
        struct.put("api_key", api_key);
        // Limit the results to images with at least region level accuracy on the location
        struct.put("accuracy", MIN_ACCURACY);
        // Limit the results to images with geo information
        struct.put("has_geo", "1");
        // Limit the result to the following place id
        struct.put("media", "photos");
        // Use minimum and maximum upload date to define a bounding box which will result
        // in less or equal of 4000 results (more will not work due to a Flickr bug)
        // Set the minimum value of the upload date
        struct.put("min_upload_date","" + min_date);
        // Set the maximum value of the upload date
        struct.put("max_upload_date","" + max_date);
//        System.out.println("["+min_upload_date+","+max_upload_date+"]");
        if (queryAllDetails)
            struct.put("extras","description,license,date_upload,date_taken," +
                    "owner_name,last_update,geo,tags,machine_tags,views," +
                    "media,path_alias,url_o");
        // Possible values
//        description, license, date_upload, date_taken, owner_name, icon_server, original_format, last_update, geo,
//        tags, machine_tags, o_dims, views, media, path_alias, url_sq, url_t, url_s, url_m, url_z, url_l, url_o

        // Number of results per page (default is 250)
        struct.put("per_page","250");
        // Select the page in the results we would like to retrieve.
        struct.put("page", pageNumber);
        return struct;
    }

    private static long lastCall = (new Date()).getTime();

    /**
     * This method makes a call to the API given the predefined parameters.
     *
     * @return Returns a String containing the response from the server.
     */
    public String make_call(XmlRpcClient client, Hashtable<String,Object> struct) {
        long now = (new Date()).getTime();
//        System.out.println((now-lastCall) + " call");
        lastCall = now;
        // Create a Vector that will contain the parameters
        Vector<Hashtable<String,Object>> params = new Vector<Hashtable<String,Object>>();
        // Add the parameters according to the struct that was defined before
        params.addElement(struct);
        // Send the request andResult that should be written to file receive the response
        String result = "";
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
//            try {
//                System.out.println(result);
//                System.out.println("Will sleep for "+retry_current_sleep+" and then retry.");
//                System.out.println("Intermediate stats: Calls: ++ SUCCESS ++ : "+remote_calls_succeeded+" | -- FAILED -- : " + remote_calls_failed + " ( "+formatter.format(remote_calls_failed* 100. / remote_calls_succeeded)+" %)");
//                Thread.sleep(retry_current_sleep);
//            } catch (InterruptedException ex) {
//                System.err.println("Thread was interrrupted. " + ex.getMessage());
//            }
//            // Double the retry sleep
//            retry_current_sleep *= 2;
//            return make_call(client, struct);
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
     * This method will perform the actual crawling.
     *
     * As long as we have not reached the timestamp of the start of the crawl,
     * we will crawl Flickr in intervals of just less than 4000 pictures.
     */
    public void identifyIntervals(String outputFile){
        try {
            PrintWriter out = new PrintWriter(new FileWriter(outputFile, true), true);
            // Set some initial values
            this.numberOfTotalResults = getTotalNumberOfResults();
            System.out.println(numberOfTotalResults);
            long start = System.currentTimeMillis();

            while (this.min_upload_date > this.end_date){
                // Find a good interval limiter to get the last 4000 pictures
                // and store it as an IntervalResult
                IntervalResult ir = findTimeInterval(numberOfTotalResults);
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
            System.exit(0);
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
    private IntervalResult findTimeInterval(int numberOfTotalResults){

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
                System.out.println(numberOfResults + "\t<<\t["+this.min_upload_date+","+this.max_upload_date+"]");
                // skip this time interval as there seems to be something wrong?
                if (numberOfResults == 0) {
                    skip = true;
                }
//                // Early exit if we have all the photos
//                if(numberOfResults < ACCEPT_THRESHOLD && (this.resultsFound + numberOfResults) >= numberOfTotalResults) {
//                    this.min_upload_date = 0;
//                }
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
                    System.out.println(numberOfResults + "\t>>\t["+this.min_upload_date+","+this.max_upload_date+"]");
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
            double percentage = (this.resultsFound / (numberOfTotalResults * 1.)) * 100;
            System.out.println(" ** Results so far: "+ this.resultsFound +"/" + numberOfTotalResults + " ("+ formatter.format(percentage) +" %) **");
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

        for (IntervalResult ir : queue) {
            // Create the directory if it does not exist
            if (!outputDir.exists())
                outputDir.mkdirs();

            new DownloadWorker(this, this.clients, ir, outputDir.toString()).download();
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

    /**Result that should be written to file
     * Helper method for parsing the the number of pages for the results from a query.
     */
    private int getNumberOfPages(String result){
        int startIndex = result.indexOf("pages=\"") + "pages=\"".length();
        int stopIndex = result.indexOf("\"",startIndex+1);
        return Integer.parseInt(result.substring(startIndex,stopIndex));
    }

    /**
     * This is a helper function that allows to check the total number
     * of results available for a specific query.
     * @return The number of results available on Flickr for the query.
     */
    private int getTotalNumberOfResults(){
        long old_min_upload_date = this.min_upload_date;
        long old_max_upload_date = this.max_upload_date;
        this.min_upload_date = 0;
        this.max_upload_date = this.max_upload_date_start;
        // Perform an call for all the results
        String response = call_service(false);
        this.min_upload_date = old_min_upload_date;
        this.max_upload_date = old_max_upload_date;
        // Fetch the number of results
        int number = getNumberOfResults(response);
        return number;
    }

    private void probeNumberOfResultsInRecentPast() {

        System.out.println(getTotalNumberOfResults());
    }

    /**
     * This method configures JAVA for using a proxy server.
     */
    public static void setUpProxy(String host, String port) {
        System.out.println("Setting up proxy... ");
        System.getProperties().put("proxySet", "true");
        System.getProperties().put("proxyHost", host);
        System.getProperties().put("proxyPort", port);
        System.out.println("done.");
    }

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

        BufferedReader file = null;
        int skipped = 0;
        int results = 0;
        String line = "";
        boolean matched_last_interval = lastInterval == null ? true : false;
        try {
            file = new BufferedReader(new FileReader(filename));
            line = file.readLine();
            while (line != null) {
                String [] values = line.split(" ");
                int min_date = Integer.parseInt(values[1]);
                int max_date = Integer.parseInt(values[3]);
                int totalPages = Integer.parseInt(values[10]);
                int numberOfResults = Integer.parseInt(values[7]);
                IntervalResult current = new IntervalResult(min_date, max_date, totalPages, numberOfResults);
                // If we resume and the results are valid
//                if (!(numberOfResults == 0 && totalPages == 0)) {
                if (matched_last_interval && !(numberOfResults == 0 && totalPages == 0)) {
                    this.totalRequestsToBeDownloaded += totalPages;
                    results += numberOfResults;
                    queue.add(current);
                }else {
                    skipped++;
                }
                if (current.equals(lastInterval)) {
                    matched_last_interval = true;
                    System.out.println("Skipped up to "+ current +", "+ skipped +" intervals.");
                }
                line = file.readLine();
            }
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
                        int results = 0;
                        long max_upload_date_start = -1;
                        while (line != null) {
                            previousLine = line;
                            String [] values = line.split(" ");
                            if (max_upload_date_start < 0)
                                max_upload_date_start = Integer.parseInt(values[3]);
                            results += Integer.parseInt(values[7]);
                            line = file.readLine();
                        }
                        file.close();
                        // Parse the data for resuming from the end of this file
                        if (previousLine != null) {
                            String [] values = previousLine.split(" ");
                            int min_date = Integer.parseInt(values[1]);
                            crawler.setMax_upload_date_start(max_upload_date_start);
                            crawler.setMax_upload_date(min_date - 1);
                            crawler.setMin_upload_date(min_date - 2);
                            crawler.setResultsFound(results);
                            System.out.println("Resuming from " + min_date);
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