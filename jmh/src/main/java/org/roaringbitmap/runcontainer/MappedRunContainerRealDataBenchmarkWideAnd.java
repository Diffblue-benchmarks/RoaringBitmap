package org.roaringbitmap.runcontainer;


import it.uniroma3.mat.extendedset.intset.ConciseSet;
import it.uniroma3.mat.extendedset.intset.ImmutableConciseSet;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.roaringbitmap.ZipRealDataRetriever;
import org.roaringbitmap.buffer.BufferFastAggregation;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import com.googlecode.javaewah32.EWAHCompressedBitmap32;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@SuppressWarnings("rawtypes")
public class MappedRunContainerRealDataBenchmarkWideAnd {

    static ConciseSet toConcise(int[] dat) {
        ConciseSet ans = new ConciseSet();
        for (int i : dat) {
            ans.add(i);
        }
        return ans;
    }
    
    // only include the first count items
    // Note: if you application is routinely aggregating
    // hundreds or thousands of bitmaps, you are maybe missing
    // optimization opportunities (e.g., one can precompute
    // some aggregates) so we mostly care for "moderate"
    // queries.
    protected static Iterator limit(final int count, final Iterator x) {
        
        return new Iterator(){
            int pos = 0;

            @Override
            public boolean hasNext() {
                return (pos < count) && (x.hasNext());
            }

            @Override
            public Object next() {
                pos++;
                return x.next();
            }
            
        };
    }
    
    @Benchmark
    public int Roaring_naive(BenchmarkState benchmarkState) {
        int answer = BufferFastAggregation.naive_and(limit(benchmarkState.count,benchmarkState.mac.iterator()))
               .getCardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }


    @Benchmark
    public int RoaringWithRun_naive(BenchmarkState benchmarkState) {
        int answer = BufferFastAggregation.naive_and(limit(benchmarkState.count,benchmarkState.mrc.iterator()))
               .getCardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }

    @Benchmark
    public int Roaring_withsort(BenchmarkState benchmarkState) {
        int answer = BufferFastAggregation.and(limit(benchmarkState.count,benchmarkState.mac.iterator()))
               .getCardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }


    @Benchmark
    public int RoaringWithRun_withsort(BenchmarkState benchmarkState) {
        int answer = BufferFastAggregation.and(limit(benchmarkState.count,benchmarkState.mrc.iterator()))
               .getCardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }    
    
    @Benchmark
    public int Concise_whatever(BenchmarkState benchmarkState) {
        ImmutableConciseSet bitmapor = ImmutableConciseSet.intersection(limit(benchmarkState.count,benchmarkState.cc.iterator()));
        int answer = bitmapor.size();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug ");
        return answer;
    }
    

    @Benchmark
    public int Concise_naive(BenchmarkState benchmarkState) {
        ImmutableConciseSet bitmapor = benchmarkState.cc.get(0);
        for (int j = 1; j < Math.min(benchmarkState.count, benchmarkState.cc.size()) ; ++j) {
            bitmapor = ImmutableConciseSet.intersection(bitmapor,benchmarkState.cc.get(j));
        }
        int answer = bitmapor.size();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }

    

    @Benchmark
    public int EWAH_naive(BenchmarkState benchmarkState) {
        Iterator i = limit(benchmarkState.count,benchmarkState.ewah.iterator());
        EWAHCompressedBitmap bitmapor = (EWAHCompressedBitmap) i.next();
        while(i.hasNext())
            bitmapor = bitmapor.and((EWAHCompressedBitmap) i.next());
        int answer = bitmapor.cardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;
    }
    
    @Benchmark
    public int EWAH32_naive(BenchmarkState benchmarkState) {
        Iterator i = limit(benchmarkState.count,benchmarkState.ewah32.iterator());
        EWAHCompressedBitmap32 bitmapor = (EWAHCompressedBitmap32) i.next();
        while(i.hasNext())
            bitmapor = bitmapor.and((EWAHCompressedBitmap32) i.next());
        int answer = bitmapor.cardinality();
        if(answer != benchmarkState.expectedvalue)
            throw new RuntimeException("bug");
        return answer;

    }


    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param ({// putting the data sets in alpha. order
            "census-income", "census1881",
            "dimension_008", "dimension_003", 
            "dimension_033", "uscensus2000", 
            "weather_sept_85", "wikileaks-noquotes",
            "census-income_srt","census1881_srt",
            "weather_sept_85_srt","wikileaks-noquotes_srt"
        })
        String dataset;
        public int expectedvalue = 0;
        
        protected int count = 8;// arbitrary number but warning: when increasing this number 
        // check that reported timings increase monotonically, I found that as of ~12, they sharply decreased
        // for some schemes, suggesting that the benchmark was defeated.
        
        List<File> createdfiles = new ArrayList<File>();

        List<ImmutableRoaringBitmap> mrc = new ArrayList<ImmutableRoaringBitmap>();
        List<ImmutableRoaringBitmap> mac = new ArrayList<ImmutableRoaringBitmap>();
        List<ImmutableConciseSet> cc = new ArrayList<ImmutableConciseSet>();
        List<EWAHCompressedBitmap> ewah = new ArrayList<EWAHCompressedBitmap>();
        List<EWAHCompressedBitmap32> ewah32 = new ArrayList<EWAHCompressedBitmap32>();

        public BenchmarkState() {
        }
        
        
        public List<ImmutableRoaringBitmap> convertToImmutableRoaring(List<MutableRoaringBitmap> source) throws IOException {
            System.out.println("Setting up memory-mapped file. (Can take some time.)");
            File tmpfile = File.createTempFile("roaring", "bin");
            createdfiles.add(tmpfile);
            tmpfile.deleteOnExit();
            final FileOutputStream fos = new FileOutputStream(tmpfile);
            final DataOutputStream dos = new DataOutputStream(fos);
            
            for(MutableRoaringBitmap rb1 : source)
                rb1.serialize(dos);
            
            final long totalcount = fos.getChannel().position();
            System.out.println("[roaring] Wrote " + totalcount / 1024 + " KB");
            dos.close();
            final RandomAccessFile memoryMappedFile = new RandomAccessFile(tmpfile, "r");
            ByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, totalcount);
            ArrayList<ImmutableRoaringBitmap> answer = new ArrayList<ImmutableRoaringBitmap>(source.size());
            while(out.position()< out.limit()) {
                    final ByteBuffer bb = out.slice();
                    MutableRoaringBitmap equiv = source.get(answer.size());
                    ImmutableRoaringBitmap newbitmap = new ImmutableRoaringBitmap(bb);       
                    if(!equiv.equals(newbitmap)) throw new RuntimeException("bitmaps do not match");
                    answer.add(newbitmap);
                    out.position(out.position() + newbitmap.serializedSizeInBytes());
            }
            memoryMappedFile.close();
            return answer;
        }
        
        public List<ImmutableConciseSet> convertToImmutableConcise(List<ConciseSet> source) throws IOException {
            System.out.println("Setting up memory-mapped file. (Can take some time.)");
            File tmpfile = File.createTempFile("concise", "bin");
            createdfiles.add(tmpfile);
            tmpfile.deleteOnExit();
            final FileOutputStream fos = new FileOutputStream(tmpfile);
            final DataOutputStream dos = new DataOutputStream(fos);
            int[] sizes = new int[source.size()];
            int pos = 0;
            for(ConciseSet cc : source) {
                byte[] data = ImmutableConciseSet.newImmutableFromMutable(cc).toBytes();
                sizes[pos++] = data.length;
                fos.write(data);
            }
            final long totalcount = fos.getChannel().position();
            System.out.println("[concise] Wrote " + totalcount / 1024 + " KB");
            dos.close();
            RandomAccessFile  memoryMappedFile = new RandomAccessFile(tmpfile, "r");
            ByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, totalcount);
            ArrayList<ImmutableConciseSet> answer = new ArrayList<ImmutableConciseSet>(source.size());
            while(out.position() < out.limit()) {
                    byte[] olddata = ImmutableConciseSet.newImmutableFromMutable(source.get(answer.size())).toBytes();
                    final ByteBuffer bb = out.slice();
                    bb.limit(sizes[answer.size()]);
                    ImmutableConciseSet newbitmap = new ImmutableConciseSet(bb);
                    byte[] newdata = newbitmap.toBytes();
                    if(!Arrays.equals(olddata, newdata))
                       throw new RuntimeException("bad concise serialization");
                    answer.add(newbitmap);
                    out.position(out.position() + bb.limit());
            }
            memoryMappedFile.close();
            return answer;
        }

        public List<EWAHCompressedBitmap> convertToImmutableEWAH(List<EWAHCompressedBitmap> source) throws IOException {
            System.out.println("Setting up memory-mapped file. (Can take some time.)");
            File tmpfile = File.createTempFile("ewah", "bin");
            createdfiles.add(tmpfile);
            tmpfile.deleteOnExit();
            final FileOutputStream fos = new FileOutputStream(tmpfile);
            final DataOutputStream dos = new DataOutputStream(fos);
            for(EWAHCompressedBitmap cc : source) 
                cc.serialize(dos);
            final long totalcount = fos.getChannel().position();
            System.out.println("[EWAHCompressedBitmap] Wrote " + totalcount / 1024 + " KB");
            dos.close();
            RandomAccessFile  memoryMappedFile = new RandomAccessFile(tmpfile, "r");
            ByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, totalcount);
            ArrayList<EWAHCompressedBitmap> answer = new ArrayList<EWAHCompressedBitmap>(source.size());
            while(out.position() < out.limit()) {
                final ByteBuffer bb = out.slice();
                EWAHCompressedBitmap equiv = source.get(answer.size());
                EWAHCompressedBitmap newbitmap = new EWAHCompressedBitmap(bb);       
                if(!equiv.equals(newbitmap)) throw new RuntimeException("bitmaps do not match");
                answer.add(newbitmap);
                out.position(out.position() + newbitmap.serializedSizeInBytes());
            }
            memoryMappedFile.close();
            return answer;
        }

        public List<EWAHCompressedBitmap32> convertToImmutableEWAH32(List<EWAHCompressedBitmap32> source) throws IOException {
            System.out.println("Setting up memory-mapped file. (Can take some time.)");
            File tmpfile = File.createTempFile("ewah32", "bin");
            createdfiles.add(tmpfile);
            tmpfile.deleteOnExit();
            final FileOutputStream fos = new FileOutputStream(tmpfile);
            final DataOutputStream dos = new DataOutputStream(fos);
            for(EWAHCompressedBitmap32 cc : source) 
                cc.serialize(dos);
            final long totalcount = fos.getChannel().position();
            System.out.println("[EWAHCompressedBitmap32] Wrote " + totalcount / 1024 + " KB");
            dos.close();
            RandomAccessFile  memoryMappedFile = new RandomAccessFile(tmpfile, "r");
            ByteBuffer out = memoryMappedFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, totalcount);
            ArrayList<EWAHCompressedBitmap32> answer = new ArrayList<EWAHCompressedBitmap32>(source.size());
            while(out.position() < out.limit()) {
                final ByteBuffer bb = out.slice();
                EWAHCompressedBitmap32 equiv = source.get(answer.size());
                EWAHCompressedBitmap32 newbitmap = new EWAHCompressedBitmap32(bb);       
                if(!equiv.equals(newbitmap)) throw new RuntimeException("bitmaps do not match");
                answer.add(newbitmap);
                out.position(out.position() + newbitmap.serializedSizeInBytes());
            }
            memoryMappedFile.close();
            return answer;
        }
                
        @TearDown
        public void clean() {
            mrc.clear();
            mac.clear();
            cc.clear();
            ewah.clear();
            ewah32.clear();
            for(File f: createdfiles)
                f.delete();
        }

                
        @Setup
        public void setup() throws Exception {
            ZipRealDataRetriever dataRetriever = new ZipRealDataRetriever(dataset);
            System.out.println();
            System.out.println("Loading files from " + dataRetriever.getName());
            ArrayList<MutableRoaringBitmap> tmpac = new ArrayList<MutableRoaringBitmap>();
            ArrayList<MutableRoaringBitmap> tmprc = new ArrayList<MutableRoaringBitmap>();
            ArrayList<ConciseSet> tmpcc = new ArrayList<ConciseSet>();
            ArrayList<EWAHCompressedBitmap> tmpewah = new ArrayList<EWAHCompressedBitmap>();
            ArrayList<EWAHCompressedBitmap32> tmpewah32 = new ArrayList<EWAHCompressedBitmap32>();

            for (int[] data : dataRetriever.fetchBitPositions()) {
                MutableRoaringBitmap mbasic = MutableRoaringBitmap.bitmapOf(data);
                MutableRoaringBitmap mopti = mbasic.clone();
                mopti.runOptimize();
                if(!mopti.equals(mbasic)) throw new RuntimeException("bug");
                ConciseSet concise = toConcise(data);
                tmpac.add(mbasic);
                tmprc.add(mopti);
                tmpcc.add(concise);
                tmpewah.add(EWAHCompressedBitmap.bitmapOf(data));
                tmpewah32.add(EWAHCompressedBitmap32.bitmapOf(data));

            }
            mrc = convertToImmutableRoaring(tmprc);
            mac = convertToImmutableRoaring(tmpac);
            cc = convertToImmutableConcise(tmpcc);
            ewah = convertToImmutableEWAH(tmpewah);
            ewah32 = convertToImmutableEWAH32(tmpewah32);
            count = mac.size();
            System.out.println("# aggregating the first "+count+" bitmaps out of "+tmpac.size());

            if((mrc.size() != mac.size()) || (mac.size() != cc.size()))
                throw new RuntimeException("number of bitmaps do not match.");
            expectedvalue = BufferFastAggregation.naive_and(limit(count,mrc.iterator()))
                    .getCardinality();
            
        }

    }

}