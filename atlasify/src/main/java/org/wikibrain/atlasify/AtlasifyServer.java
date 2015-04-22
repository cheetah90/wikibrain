package org.wikibrain.atlasify;
/**
 * Created by toby on 8/13/14.
 */



import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;




public class AtlasifyServer {

    private static int portNo;
    private static String baseUrl;
    private static URI baseURI;

    public AtlasifyServer(String baseUrl, int portNo){
        this.portNo = portNo;
        this.baseUrl = baseUrl;
        baseURI = getBaseURI();
    }



    private static URI getBaseURI() {
        System.out.println("Creating Server with URL " + baseUrl + " and port number " + portNo);
        return UriBuilder.fromUri(baseUrl).port(portNo).build();
    }




    protected static HttpServer startServer() throws IOException{
        final ResourceConfig rc = new ResourceConfig().packages("org.wikibrain.atlasify");
        rc.register(org.wikibrain.atlasify.CORSFilter.class);
        rc.register(JacksonFeature.class);

        System.out.println("Staring grizzly...");

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseURI, rc);
        return server;
    }

    public static ByteArrayOutputStream logger = new ByteArrayOutputStream();
    public static final boolean useLocalLogger = true;
    public static HttpServer server;
    public void startAtlasify() throws IOException {
        if (useLocalLogger) {
            PrintStream stdOut = System.out;
            System.setOut(new OutputRedirector(logger, stdOut));
            PrintStream stdErr = System.err;
            System.setErr(new OutputRedirector(logger, stdErr));
        }

        server = startServer();
        System.out.println(String.format("Jersey app started with WADL available at "
                + "%sapplication.wadl\nTry out %shelloworld\nHit enter to stop it...",
                baseURI, baseURI));

    }
    public void stopAtlasify(){
        server.shutdownNow();
    }

}
