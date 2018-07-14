package uk.co.compendiumdev.thingifier.api;

public class RoutingDefinition {
    private final RoutingVerb verb;
    private final String url;
    private final RoutingStatus routingStatus;
    private final ResponseHeader header;

    public RoutingDefinition(RoutingVerb verb, String url, RoutingStatus routingStatus, ResponseHeader header) {
        this.verb = verb;
        this.url = url;
        this.routingStatus = routingStatus;
        this.header = header;
    }

    public RoutingVerb verb() {
        return this.verb;
    }

    public RoutingStatus status() {
        return routingStatus;
    }

    public String url() {
        return url;
    }

    public String header() {
        if(header.headerName==null) {
            return "";
        }

        return header.headerName;
    }

    public String headerValue() {
        if(header.headerValue==null) {
            return "";
        }

        return header.headerValue;
    }
}
