# SAHI-EHR: Adaptive Hybrid Indexing for Streaming Electronic Health Records

**Academic Year:** 2026–27 | **Course:** Data Structures and Algorithms (CSE E1CSH25602)  
**Institution:** Alliance University, Bengaluru, India  
**Team:** Bharath Kumar, Jhanavi, Ashutosh (M. Tech CSE)

## 📌 Project Overview
Intensive Care Unit (ICU) data streams generate continuous, bursty physiological telemetry alongside diagnostic ICD-10 annotations. Standard single-paradigm indexes (like B+ Trees or LSM Trees) struggle with mixed workloads, causing severe latency spikes during data bursts. 

**SAHI-EHR** solves this trilemma by coordinating three specialized sub-indices through a drift-aware staging controller:
1. **Temporal Sub-Index (Ordered Tree):** Maintains sequential ordering of records by timestamp for efficient time-window scans.
2. **Acuity Priority Sub-Index (Augmented Max-Heap):** A complete binary max-heap augmented with an $O(1)$ reverse positional `HashMap`, bounding dynamic severity updates to $O(\log K)$ and top-$k$ triage queries to $O(k \log k)$.
3. **Ontology Sub-Index (Compact Radix Trie):** An edge-compressed Patricia Radix Trie for instant diagnostic ICD-10 prefix autocomplete.
4. **Adaptive Staging Buffer:** Absorbs high-volume telemetry bursts and flushes into the temporal tree in amortized $O(1)$ time (proven via Tarjan's Potential Method).

## 🚀 How to Run the Benchmark

### Prerequisites
* Java Development Kit (JDK 17 or higher)
* Minimum 4 GB RAM assigned to the Java Virtual Machine

### Execution Steps
1. Navigate to the `code/` directory:
   ```bash
   cd code
