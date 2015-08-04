# Introduction
Analysis of genotypes and gene expression phenotype (eQTLs) using ADAM and Spark

This demonstration program performs linear regression of genotype vs.
gene expression phenotypes (eQTLs), making use of ADAM Parquet Genotype file format as input, and Spark.

Projects used here include:
* ADAM from Big Data Genomics
* Spark
* Apache math3 commons java package

# Background
In statistical genetics, associations tests are often performed between genotype and phenotype.  In the case of a continuous trait such as height, linear regression can be used to associate the dosage (zero, one, or two copies) of an alternative allele at a position in the genome with a quantitative trait.  

Over the past decade it has become possible to measure simultaneously thousands of molecular gene expression phenotypes, the amount of a gene transcript made by a cells (or a cultured cell line) derived from a specific person, and to associate that molecular phenotype with genetic variation within a sample population.   Thes studies are often termed (eQTL) for expression quantitative trait loci studies.

In this demonstration program, genotypes are formated into the efficient ADAM genotype format using Parquet and then Spark is used to perform hundreds of millions of linear regression tests.  

For example, in tests described below 450,000 variants with an alternative allele frequency between 10 and 90% are analyzed against 5000 gene exprssion phenotypes in a population of 54 individuals.   Thus, there is a need to perform 450,000 * 5000 = 2.25 billion statistical tests.  

As discussed in the note at end, dealing with the multiple testing problem in interpreting the output of these tests, is another area where Spark could be applied.


# Source Data and References

Datasets analyzed here are derived from those at: (http://jungle.unige.ch/rnaseq_CEU60/)

Cited in the paper: (http://www.ncbi.nlm.nih.gov/pmc/articles/PMC3836232/)

#Building

To build from source first [download and install Maven](http://maven.apache.org/download.cgi).
Then at the command line type:

```
mvn clean package
```

This will produce the JAR file target/uberScalajar-Spark_eQTL_assoc-0.1-SNAPSHOT.jar

#Running
Download Spark version 1.2.0 (http://spark.apache.org/downloads.html) and unpack it on your machine.

To run from the base of this cloned repository using example chr22 and chr2 data in the data/ directory, example usage:

```
YOUR_SPARK_HOME/bin/spark-submit YOUR_SPARK_HOME/bin/spark-submit   \
     --class "org.justinpaschall.EQTLAssoc" \
     --master local[*]   \
     --driver-memory 4G \
     --executor-memory 4G \
      target/uberScalajar-Spark_eQTL_assoc-0.1-SNAPSHOT.jar data/probes100 \
       data/chr22vcf.adam 8 output_analysis_chr22_probes100
```

The four command line parameters defined by their order are
```
<listOfProbes> <vcfdata> <numOfSparkPartitions> <outputDirName>
```

# Deploying and Testing Scalability on AWS EC2

Code and data were deployed on AWS EC2, and tested using

* 32 executors with 32 Spark partitions ( 16 m3.large - 32 cores )
* 16 executors with 16 Spark partitions ( 8 m3.large - 16 cores )
* 8 executors with 8 Spark partitions ( 4 m3.large - 8 cores )
* Local machine with 8 Spark partitions ( 1 quadcore HT )

Linear regresion against 100, 1000, 5000, 10000 gene expression phenotypes

######Analyzing ~77,000 variants from chr2  (*Times in Minutes*)
|             | 32 cores | 16 cores | 8 cores | local - quadcore HT  |
| ----------- | -------- |:--------:|:-------:|:---------------------:|
| 100 pheno   | 1.5      | 1.92     | 1.96    | 2.02                  |
| 1000 pheno  | 3.9      | 5.7      | 10.4    | 14.1                  |
| 5000 pheno  | 13.9     | 23.29    | 47.1    | pending               |
| 10000 pheno | 27.7     | pending  | 94      | 135.1                 |

Analysis time scales linearally with addition of processors, there is a constant cost to load or sort genotypes at beginning that needs to be analyzed with the addition of more samples

######Analyzing ~450,000 variants from chr2  (*Times in Minutes*)

|             | 32 cores | 16 cores | 8 cores | local - quadcore HT  |
| ----------- | -------- |:--------:|:-------:|:---------------------:|
| 100 pheno   | 2.7      | pending  | 8.32    | 15.5                  |
| 1000 pheno  | 15.7     | pending  | 55.1    | 74.9                  |
| 5000 pheno  | 76.7     | pending  | failed* | not attempted         |
| 10000 pheno | failed*  | pending  | 94      | not attempted         |

As expected, scales linerally with number of variants, numbers below are approx (478,000/78,000) = 5.5 times those in above table

*failed runs repeatidly just terminated in AWS, suspicously both at 2.2 hours, needs futher investigation, machines appear to have plenty of total memory unused near time of failure

### Comparison with PLINK

PLINK is a standard tool used by genetics researchers, implementing a vast number of statistical tests.

PLINK's linear regression test was used as benchmark for comparison.


Spot checking results indicate agreement within rounding error.

PLINK is a single threaded application.  
Using the same test set of 10000 phenotypes and 78000 chr 22 variants above requring 135 minutes on the local machine, a single instances of PLINK finishes in XXXX minutes.  Scaled to 8 independent threads, as the job can be arbitaritly split, PLINK would finish in XXXX, however on a single machine there appear to be contention for resources between theads.   PLINK is thus X times faster, not surpring given its C implementation, however signficant work is required to manage jobs on a cluster and recover from error, and recombine results, exactly what Spark does for us.




Todo: 
* Increase the number of samples to assess scaling properties of the initial groupBy phase
* Determine the cause of failure seen above at chr2 10000 pheno 32 core, and chr 22 5000 pheno 16 core 
* Empirical and machine learning methods to assess significance / reduce search space

#### Note on statistical signifcance
This project demonstrates a way to efficiently parallelize these statistical tests using Spark, however scientific interpretation requires adjustment for the many millions of tests performed.  Corrections need to be made to ascertain signfificance based on prior hypothesis or otherwise the number of tests need to limited - often first (cis-acting) eQTLs are considered where the variant is in or near the gene for which expression is being tested for association.  An adaptive machine learning approach also using Spark may be a good option to narrow this search space, or to adaptively expand the search based on network analysis.

#Credits
Inspirations, including structure of this README from:
(https://github.com/nfergu/popstrat)

Keep watching the exciting work of [Big Data Genomics](http://bdgenomics.org)

