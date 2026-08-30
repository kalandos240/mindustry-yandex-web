package mindustry.net;

import mindustry.net.Packets.*;

import java.io.*;

/** Web/single-thread compatible stream assembly used by the TeaVM target. */
public class Streamable extends Packet{
    public transient ByteArrayInputStream stream;

    @Override
    public int getPriority(){
        return priorityHigh;
    }

    @Override
    public boolean allow(boolean server){
        return !server;
    }

    public boolean incremental(){
        return false;
    }

    public static class StreamBuilder{
        public final int id;
        public final byte type;
        public final int total;
        public final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        public final boolean incremental;
        public final IncrementalStream incrementalStream;
        public int received;

        public StreamBuilder(StreamBegin begin, boolean incremental){
            id = begin.id;
            type = begin.type;
            total = begin.total;
            this.incremental = incremental;
            incrementalStream = incremental ? new IncrementalStream() : null;
        }

        public float progress(){
            return (float)received / total;
        }

        public void add(byte[] bytes){
            received += bytes.length;
            try{
                if(incrementalStream != null){
                    incrementalStream.add(bytes);
                }else{
                    stream.write(bytes);
                }
            }catch(IOException e){
                throw new RuntimeException(e);
            }
        }

        public Streamable build(){
            Streamable s = Net.newPacket(type);
            s.stream = new ByteArrayInputStream(stream.toByteArray());
            return s;
        }

        public boolean isDone(){
            return received >= total;
        }

        public void close(){
            if(incrementalStream != null) incrementalStream.close();
        }
    }

    /**
     * Browser event-loop stream buffer. It intentionally never blocks: WebSocket
     * delivery will append chunks from the browser callback and consumers read the
     * bytes already available on the main thread.
     */
    static class IncrementalStream extends InputStream{
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean closed, finished;
        private int offset;
        private final byte[] singleByte = new byte[1];

        void add(byte[] bytes) throws IOException{
            if(closed || finished || bytes == null || bytes.length == 0) return;
            if(offset == buffer.size()){
                buffer.reset();
                offset = 0;
            }
            buffer.write(bytes);
        }

        @Override
        public int read(byte[] out, int off, int len) throws IOException{
            if(off < 0 || len < 0 || len > out.length - off) throw new IndexOutOfBoundsException();
            if(len == 0) return 0;
            if(closed) throw new IOException("Stream is closed");

            byte[] available = buffer.toByteArray();
            int remaining = available.length - offset;
            if(remaining <= 0) return finished ? -1 : 0;

            int count = Math.min(len, remaining);
            System.arraycopy(available, offset, out, off, count);
            offset += count;
            return count;
        }

        @Override
        public int read() throws IOException{
            int result = read(singleByte, 0, 1);
            return result <= 0 ? result : singleByte[0] & 0xff;
        }

        public void finish(){
            finished = true;
        }

        @Override
        public void close(){
            closed = true;
            buffer.reset();
            offset = 0;
        }
    }
}
