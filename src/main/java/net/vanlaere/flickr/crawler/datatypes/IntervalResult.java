package net.vanlaere.flickr.crawler.datatypes;

/**
 * This class represents an interval in time that will
 * result in just less than 4000 Flickr results.
 *
 * Elements from IntervalResult can be queued for threaded
 * processing later on in the crawler.
 *
 * @author oliviervanlaere@gmail.com
 */
public class IntervalResult {

    private long min_date;

    public long getMinDate(){
        return min_date;
    }

    private long max_date;

    public long getMaxDate(){
        return max_date;
    }

    private int totalPages;

    public int getTotalPages(){
        return totalPages;
    }

    private int numberOfResults;

    public int getNumberOfResults(){
        return numberOfResults;
    }

    public IntervalResult(long min_date, long max_date, int totalPages, int numberOfResults) {
        this.min_date = min_date;
        this.max_date = max_date;
        this.totalPages = totalPages;
        this.numberOfResults = numberOfResults;
    }

    @Override
    public String toString(){
        String result = "";
        result += "[ " + min_date + " , " + max_date + " ] results in " +
                    numberOfResults + " results over " + totalPages + " pages.";
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof IntervalResult) {
            IntervalResult iro = (IntervalResult) o;
            return (this.min_date == iro.min_date) && (this.max_date == iro.max_date);
        }
        else
            return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (int) (this.min_date ^ (this.min_date >>> 32));
        hash = 97 * hash + (int) (this.max_date ^ (this.max_date >>> 32));
        return hash;
    }
}