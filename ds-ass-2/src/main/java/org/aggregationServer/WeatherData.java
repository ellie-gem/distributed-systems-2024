package org.aggregationServer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WeatherData {
    private String id;
    private String name;
    private String state;
    @JsonProperty("time_zone") // have to do this bc of the way Jackson JSON naming is configured (camel case)
    private String timeZone;
    private double lat;
    private double lon;
    @JsonProperty("local_date_time")
    private String localDateTime;
    @JsonProperty("local_date_time_full")
    private String localDateTimeFull;
    @JsonProperty("air_temp")
    private double airTemp;
    @JsonProperty("apparent_t")
    private double apparentTemp;
    private String cloud;
    private double dewpt;
    private double press;
    @JsonProperty("rel_hum")
    private int relHum;
    @JsonProperty("wind_dir")
    private String windDir;
    @JsonProperty("wind_spd_kmh")
    private int windSpdKmh;
    @JsonProperty("wind_spd_kt")
    private int windSpdKt;

    // Default constructor
    public WeatherData() {}

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public String getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(String localDateTime) {
        this.localDateTime = localDateTime;
    }

    public String getLocalDateTimeFull() {
        return localDateTimeFull;
    }

    public void setLocalDateTimeFull(String localDateTimeFull) {
        this.localDateTimeFull = localDateTimeFull;
    }

    public double getAirTemp() {
        return airTemp;
    }

    public void setAirTemp(double airTemp) {
        this.airTemp = airTemp;
    }

    public double getApparentTemp() {
        return apparentTemp;
    }

    public void setApparentTemp(double apparentTemp) {
        this.apparentTemp = apparentTemp;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public double getDewpt() {
        return dewpt;
    }

    public void setDewpt(double dewpt) {
        this.dewpt = dewpt;
    }

    public double getPress() {
        return press;
    }

    public void setPress(double press) {
        this.press = press;
    }

    public int getRelHum() {
        return relHum;
    }

    public void setRelHum(int relHum) {
        this.relHum = relHum;
    }

    public String getWindDir() {
        return windDir;
    }

    public void setWindDir(String windDir) {
        this.windDir = windDir;
    }

    public int getWindSpdKmh() {
        return windSpdKmh;
    }

    public void setWindSpdKmh(int windSpdKmh) {
        this.windSpdKmh = windSpdKmh;
    }

    public int getWindSpdKt() {
        return windSpdKt;
    }

    public void setWindSpdKt(int windSpdKt) {
        this.windSpdKt = windSpdKt;
    }

    @Override
    public String toString() {
        return "WeatherData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", state='" + state + '\'' +
                ", timeZone='" + timeZone + '\'' +
                ", lat=" + lat +
                ", lon=" + lon +
                ", localDateTime='" + localDateTime + '\'' +
                ", localDateTimeFull='" + localDateTimeFull + '\'' +
                ", airTemp=" + airTemp +
                ", apparentTemp=" + apparentTemp +
                ", cloud='" + cloud + '\'' +
                ", dewpt=" + dewpt +
                ", press=" + press +
                ", relHum=" + relHum +
                ", windDir='" + windDir + '\'' +
                ", windSpdKmh=" + windSpdKmh +
                ", windSpdKt=" + windSpdKt +
                '}';
    }
}