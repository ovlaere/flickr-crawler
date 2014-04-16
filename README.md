flickr-crawler
==============

This project provides code to crawl the Flickr API. This code has been used during my PhD to crawl for geotagged Flickr pictures.

If you have any problems using this code, just let me know.

oliviervanlaere@gmail.com

### Crawler concept

Before getting started, let's sketch the concept of this crawler.

The Flickr API allows to perform requests for data. However, due to a *feature* in the API, you cannot retrieve more than 4000 items in one call. To cope with this, this crawler will try to determine time intervals that approach this limit but don't exceed it. When these intervals are identified, they are written to a file.

In a second round, you can start the crawler to download the detailed XML data for all of the intervals you have on file.

### Software requirements

* maven3
* java 1.7

### Building the code

If your maven is set up correctly, all you should do is run:

	mvn package

This provides you with a packaged jar `FlickrCrawler-1.0-SNAPSHOT.jar` that includes all dependencies. You will find this jar in the `target` folder.

### Phase 1 : Finding intervals

To scan for time intervals that contain less than 4000 items, you run the following command:

	java -jar target/FlickrCrawler-1.0-SNAPSHOT.jar API_KEY scan TIMESTAMP INTERVAL_FILE DATA_FOLDER

This command requires:

* *API_KEY* : Your Flickr API key
* The `scan` command
* *TIMESTAMP* : The timestamp to stop. The crawler starts at the current time and runs backward until this end time.
* *INTERVAL_FILE* : The filename of the file that will store the intervals that are found
* *DATA_FOLDER* : A folder that is going to be used to store the data

### Phase 2 : Downloading data

Once the intervals are written to file, you can download the actual data for these intervals. To this end, run:

	java -jar target/FlickrCrawler-1.0-SNAPSHOT.jar API_KEY download TIMESTAMP INTERVAL_FILE DATA_FOLDER

This command requires:

* *API_KEY* : Your Flickr API key
* The `download` command
* *TIMESTAMP* : The timestamp to stop. The crawler starts at the current time and runs backward until this end time.
* *INTERVAL_FILE* : The filename of the file that will store the intervals that are found
* *DATA_FOLDER* : A folder that is going to be used to store the data

The data will be stored in *DATA_FOLDER*, and split into folders `chunk_001`, `chunk_002`, ... each containing up to 10 000 XML files. This is to prevent directories that contain too much files to handle.

### Postprocessing : Parsing data

Once the data is downloaded, you can parse the data using the provided parser:

	java -cp target/FlickrCrawler-1.0-SNAPSHOT.jar net.vanlaere.flickr.parse.FlickrXMLParser DATA_FOLDER FINAL_FILE

This command requires:

* *DATA_FOLDER* : The folder where the data that needs to be parsed is stored
* *FINAL_FILE* : The file where the parsed XML and thus final data is going to be written to

### Resuming

In case something goes wrong, you can safely abort the `scan` or `download`. 

* In case of `scan`, it's recommended to remove the last interval from *INTERVAL_FILE*. After a restart, the crawler will resume from the last known interval, safeguarding all work that was done until that point.

* In case of `download`, the crawler will try to identify the point of failure based on the files found in *DATA_FOLDER*. It will resume downloading from that point on.
