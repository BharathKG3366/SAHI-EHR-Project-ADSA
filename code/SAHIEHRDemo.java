import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class SAHIEHRDemo {

    public static class PatientRecord {
        public final long patientId;
        public final double timestamp;
        public final float[] vitals; 
        public final List<String> icdCodes;
        public float severityScore;

        public PatientRecord(long patientId, double timestamp, float[] vitals, 
                             List<String> icdCodes, float severityScore) {
            this.patientId = patientId;
            this.timestamp = timestamp;
            this.vitals = vitals;
            this.icdCodes = icdCodes;
            this.severityScore = severityScore;
        }
    }

    public static class AugmentedPriorityHeap {
        private static class HeapNode {
            long patientId;
            float severity;
            PatientRecord record;
            HeapNode(long pId, float sev, PatientRecord r) {
                this.patientId = pId; this.severity = sev; this.record = r;
            }
        }
        private final List<HeapNode> heap = new ArrayList<>();
        private final Map<Long, Integer> posMap = new HashMap<>();

        private void swap(int i, int j) {
            HeapNode nI = heap.get(i);
            HeapNode nJ = heap.get(j);
            heap.set(i, nJ);
            heap.set(j, nI);
            posMap.put(nJ.patientId, i);
            posMap.put(nI.patientId, j);
        }

        private void siftUp(int idx) {
            while (idx > 0) {
                int p = (idx - 1) / 2;
                if (heap.get(idx).severity > heap.get(p).severity) {
                    swap(idx, p);
                    idx = p;
                } else break;
            }
        }

        private void siftDown(int idx) {
            int n = heap.size();
            while (2 * idx + 1 < n) {
                int l = 2 * idx + 1, r = 2 * idx + 2, largest = idx;
                if (l < n && heap.get(l).severity > heap.get(largest).severity) largest = l;
                if (r < n && heap.get(r).severity > heap.get(largest).severity) largest = r;
                if (largest != idx) { swap(idx, largest); idx = largest; }
                else break;
            }
        }

        public synchronized void upsert(PatientRecord r) {
            if (posMap.containsKey(r.patientId)) {
                int idx = posMap.get(r.patientId);
                float oldSev = heap.get(idx).severity;
                heap.get(idx).severity = r.severityScore;
                heap.get(idx).record = r;
                if (r.severityScore > oldSev) siftUp(idx);
                else siftDown(idx);
            } else {
                heap.add(new HeapNode(r.patientId, r.severityScore, r));
                int newIdx = heap.size() - 1;
                posMap.put(r.patientId, newIdx);
                siftUp(newIdx);
            }
        }

        public synchronized List<PatientRecord> getTopK(int k) {
            List<PatientRecord> res = new ArrayList<>();
            if (heap.isEmpty() || k <= 0) return res;
            k = Math.min(k, heap.size());
            PriorityQueue<Integer> cand = new PriorityQueue<>((a, b) -> Float.compare(heap.get(b).severity, heap.get(a).severity));
            cand.offer(0);
            while (res.size() < k && !cand.isEmpty()) {
                int cur = cand.poll();
                res.add(heap.get(cur).record);
                int l = 2 * cur + 1, r = 2 * cur + 2;
                if (l < heap.size()) cand.offer(l);
                if (r < heap.size()) cand.offer(r);
            }
            return res;
        }
    }

    public static class CompactRadixTrie {
        private static class TrieNode {
            String edgeLabel;
            boolean isTerminal = false;
            String fullCode = null;
            List<PatientRecord> postings = new ArrayList<>();
            Map<Character, TrieNode> children = new HashMap<>();
            TrieNode(String label) { this.edgeLabel = label; }
        }
        private final TrieNode root = new TrieNode("");

        public synchronized void insert(String code, PatientRecord r) {
            TrieNode curr = root;
            int i = 0;
            while (i < code.length()) {
                char ch = code.charAt(i); // Fixed: code.charAt(i)
                if (!curr.children.containsKey(ch)) {
                    TrieNode leaf = new TrieNode(code.substring(i));
                    leaf.isTerminal = true; leaf.fullCode = code; leaf.postings.add(r);
                    curr.children.put(ch, leaf);
                    return;
                }
                TrieNode child = curr.children.get(ch);
                int common = 0;
                while (common < child.edgeLabel.length() && (i + common) < code.length() 
                       && child.edgeLabel.charAt(common) == code.charAt(i + common)) common++;
                if (common == child.edgeLabel.length()) {
                    i += common; curr = child;
                } else {
                    TrieNode split = new TrieNode(child.edgeLabel.substring(0, common));
                    child.edgeLabel = child.edgeLabel.substring(common);
                    split.children.put(child.edgeLabel.charAt(0), child);
                    if (i + common == code.length()) {
                        split.isTerminal = true; split.fullCode = code; split.postings.add(r);
                    } else {
                        TrieNode branch = new TrieNode(code.substring(i + common));
                        branch.isTerminal = true; branch.fullCode = code; branch.postings.add(r);
                        split.children.put(code.charAt(i + common), branch);
                    }
                    curr.children.put(ch, split);
                    return;
                }
            }
            curr.isTerminal = true; curr.fullCode = code; curr.postings.add(r);
        }

        public synchronized List<String> autocomplete(String pfx, int limit) {
            TrieNode curr = root;
            int i = 0;
            while (i < pfx.length()) {
                char ch = pfx.charAt(i); // Fixed: pfx.charAt(i)
                if (!curr.children.containsKey(ch)) return Collections.emptyList();
                TrieNode child = curr.children.get(ch);
                int common = 0;
                while (common < child.edgeLabel.length() && (i + common) < pfx.length() 
                       && child.edgeLabel.charAt(common) == pfx.charAt(i + common)) common++;
                if (i + common == pfx.length()) { curr = child; break; }
                if (common < child.edgeLabel.length()) return Collections.emptyList();
                i += common; curr = child;
            }
            List<String> res = new ArrayList<>();
            Queue<TrieNode> q = new LinkedList<>();
            q.offer(curr);
            while (!q.isEmpty() && res.size() < limit) {
                TrieNode n = q.poll();
                if (n.isTerminal) res.add(n.fullCode);
                for (TrieNode next : n.children.values()) q.offer(next);
            }
            return res;
        }
    }

    public static class SAHIEHRIndex {
        private final NavigableMap<Double, PatientRecord> temporalTree = new TreeMap<>();
        private final CompactRadixTrie ontologyTrie = new CompactRadixTrie();
        private final AugmentedPriorityHeap priorityHeap = new AugmentedPriorityHeap();
        private final List<PatientRecord> stagingBuffer = new ArrayList<>();
        private final int burstThreshold;
        private boolean isBurst = false;

        public SAHIEHRIndex(int thresh) { this.burstThreshold = thresh; }

        public synchronized void ingest(PatientRecord r) {
            priorityHeap.upsert(r);
            for (String c : r.icdCodes) ontologyTrie.insert(c, r);
            if (isBurst) {
                stagingBuffer.add(r);
                if (stagingBuffer.size() >= burstThreshold) flushBuffer();
            } else {
                temporalTree.put(r.timestamp, r);
            }
        }

        public synchronized void setBurst(boolean b) {
            this.isBurst = b;
            if (!b && !stagingBuffer.isEmpty()) flushBuffer();
        }

        public synchronized void flushBuffer() {
            for (PatientRecord r : stagingBuffer) temporalTree.put(r.timestamp, r);
            stagingBuffer.clear();
        }

        public synchronized List<PatientRecord> rangeQuery(double s, double e) {
            flushBuffer();
            return new ArrayList<>(temporalTree.subMap(s, true, e, true).values());
        }

        public synchronized List<PatientRecord> queryTopK(int k) { return priorityHeap.getTopK(k); }
        public synchronized List<String> autocomplete(String p, int l) { return ontologyTrie.autocomplete(p, l); }
    }

    public static void exportToCSV(List<PatientRecord> records, String filepath) {
        System.out.println("Exporting dataset to: " + filepath + " ...");
        File file = new File(filepath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("patient_id,timestamp,heart_rate,systolic_bp,resp_rate,spo2,icd10_codes,severity_score");
            for (PatientRecord r : records) {
                String codes = String.join(";", r.icdCodes);
                pw.printf("%d,%.2f,%.1f,%.1f,%.1f,%.1f,%s,%.1f\n",
                    r.patientId, r.timestamp, r.vitals[0], r.vitals[1], r.vitals[2], r.vitals[3], codes, r.severityScore);
            }
            System.out.println("Dataset successfully saved!");
        } catch (IOException e) {
            System.err.println("Failed to write CSV: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int dataSize = 100_000;
        SAHIEHRIndex index = new SAHIEHRIndex(5_000);
        String[] icdPool = {"I21.9", "I21.0", "I21.1", "I50.9", "J44.1", "J44.9", "E11.9", "E11.65", "A41.9", "N17.9"};
        Random rand = new Random(42);

        System.out.println("Generating 100,000 synthetic clinical records...");
        List<PatientRecord> records = new ArrayList<>(dataSize);
        double baseTime = 1700000000.0;

        for (int i = 0; i < dataSize; i++) {
            long pId = rand.nextInt(10_000);
            double t = baseTime + (i * 0.5);
            float hr = 50 + rand.nextFloat() * 90;
            float sbp = 80 + rand.nextFloat() * 100;
            float rr = 12 + rand.nextFloat() * 20;
            float spo2 = 88 + rand.nextFloat() * 12;

            float sev = 0.0f;
            if (hr > 110 || hr < 45) sev += 3.0f;
            if (sbp < 90 || sbp > 180) sev += 3.0f;
            if (rr > 25 || rr < 9) sev += 3.0f;
            if (spo2 < 92) sev += 3.0f;
            if (i % 1000 == 0) sev = 18.0f;

            List<String> codes = new ArrayList<>();
            codes.add(icdPool[rand.nextInt(icdPool.length)]);
            if (rand.nextBoolean()) codes.add(icdPool[rand.nextInt(icdPool.length)]);

            records.add(new PatientRecord(pId, t, new float[]{hr, sbp, rr, spo2}, codes, sev));
        }

        // Export to CSV in the dataset folder
        exportToCSV(records, "../dataset/mimic_iv_synthetic_stream.csv");

        // Profile Ingestion
        System.out.println("\nProfiling Benchmark Metrics...");
        long[] ingestTimes = new long[dataSize];
        index.setBurst(true);
        for (int i = 0; i < dataSize; i++) {
            long s = System.nanoTime();
            index.ingest(records.get(i));
            ingestTimes[i] = System.nanoTime() - s;
        }
        Arrays.sort(ingestTimes);

        // Profile Top-K
        int queryRuns = 10_000;
        long[] topKTimes = new long[queryRuns];
        for (int i = 0; i < queryRuns; i++) {
            long s = System.nanoTime();
            index.queryTopK(10);
            topKTimes[i] = System.nanoTime() - s;
        }
        Arrays.sort(topKTimes);

        // Profile Autocomplete
        long[] pfxTimes = new long[queryRuns];
        for (int i = 0; i < queryRuns; i++) {
            long s = System.nanoTime();
            index.autocomplete("I21", 5);
            pfxTimes[i] = System.nanoTime() - s;
        }
        Arrays.sort(pfxTimes);

        // Profile Range Query
        long[] rangeTimes = new long[queryRuns];
        for (int i = 0; i < queryRuns; i++) {
            double startT = baseTime + (i * 2.0);
            double endT = startT + 3600.0;
            long s = System.nanoTime();
            index.rangeQuery(startT, endT);
            rangeTimes[i] = System.nanoTime() - s;
        }
        Arrays.sort(rangeTimes);

        // Memory
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long memMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        System.out.println("\n=======================================================");
        System.out.println("                 BENCHMARK RESULTS                     ");
        System.out.println("=======================================================");
        System.out.printf("Ingestion Latency   : P50 = %.2f us | P99 = %.2f us\n", ingestTimes[(int)(dataSize * 0.5)]/1000.0, ingestTimes[(int)(dataSize * 0.99)]/1000.0);
        System.out.printf("Top-10 Query Latency: P50 = %.2f us | P99 = %.2f us\n", topKTimes[(int)(queryRuns * 0.5)]/1000.0, topKTimes[(int)(queryRuns * 0.99)]/1000.0);
        System.out.printf("Autocomplete Latency: P50 = %.2f us | P99 = %.2f us\n", pfxTimes[(int)(queryRuns * 0.5)]/1000.0, pfxTimes[(int)(queryRuns * 0.99)]/1000.0);
        System.out.printf("Range Scan Latency  : P50 = %.2f us | P99 = %.2f us\n", rangeTimes[(int)(queryRuns * 0.5)]/1000.0, rangeTimes[(int)(queryRuns * 0.99)]/1000.0);
        System.out.printf("Resident JVM Memory : %d MB\n", memMB);
        System.out.println("=======================================================\n");
    }
}