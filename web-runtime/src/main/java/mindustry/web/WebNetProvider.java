package mindustry.web;

import arc.func.*;
import arc.struct.*;
import mindustry.net.*;
import mindustry.net.Net.*;

import java.io.*;

/**
 * Permanent single-player networking boundary for the browser build.
 *
 * This Yandex/Web target intentionally has no multiplayer transport at all: no
 * ArcNet sockets, no WebSocket client, no LAN discovery, no host mode and no
 * remote server list. Net still exists because stock Mindustry gameplay queries
 * net.client()/net.server()/net.active(), but this provider can never transition
 * that facade into an active network session.
 */
public final class WebNetProvider implements NetProvider{
    private final Seq<NetConnection> connections = new Seq<>();

    private static IOException multiplayerDisabled(){
        return new IOException("Multiplayer is disabled in this single-player Web build");
    }

    @Override
    public void connectClient(String ip, int port, Runnable success) throws IOException{
        throw multiplayerDisabled();
    }

    @Override
    public void sendClient(Object object, boolean reliable){
        // Intentionally inert: this build never has a remote client connection.
    }

    @Override
    public void disconnectClient(){
        connections.clear();
    }

    @Override
    public void discoverServers(Cons<Host> callback, Runnable done){
        // No LAN, remote or platform-backed server discovery in the single-player build.
        if(done != null) done.run();
    }

    @Override
    public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed){
        if(failed != null) failed.get(multiplayerDisabled());
    }

    @Override
    public void hostServer(int port) throws IOException{
        throw multiplayerDisabled();
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
