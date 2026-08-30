package mindustry.web;

import arc.func.*;
import arc.struct.*;
import mindustry.net.*;
import mindustry.net.Net.*;

import java.io.*;

/**
 * Browser networking boundary for Mindustry.
 *
 * The desktop ArcNet provider depends on java.nio socket channels, UDP discovery
 * and JVM worker threads, none of which exist in TeaVM JavaScript. This provider
 * deliberately keeps those dependencies out of the Web graph. WebSocket packet
 * transport will be implemented behind this same NetProvider contract.
 */
public final class WebNetProvider implements NetProvider{
    private final Seq<NetConnection> connections = new Seq<>();

    @Override
    public void connectClient(String ip, int port, Runnable success) throws IOException{
        throw new IOException("Browser WebSocket transport is not initialized yet");
    }

    @Override
    public void sendClient(Object object, boolean reliable){
        // No active browser connection until the WebSocket transport is installed.
    }

    @Override
    public void disconnectClient(){
        connections.clear();
    }

    @Override
    public void discoverServers(Cons<Host> callback, Runnable done){
        // Browsers cannot perform UDP/LAN discovery. A platform server list can be
        // supplied later through HTTP/Yandex services without exposing ArcNet.
        if(done != null) done.run();
    }

    @Override
    public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed){
        if(failed != null) failed.get(new IOException("Raw host ping is unavailable in browsers"));
    }

    @Override
    public void hostServer(int port) throws IOException{
        throw new IOException("Hosting a raw TCP/UDP Mindustry server is unavailable in browsers");
    }

    @Override
    public Iterable<? extends NetConnection> getConnections(){
        return connections;
    }

    @Override
    public void closeServer(){
        connections.clear();
    }
}
