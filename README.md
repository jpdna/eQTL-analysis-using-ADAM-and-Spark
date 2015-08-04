# Introduction
Analysis of genotypes and gene expression phenotype (eQTLs) using ADAM and Spark

This demonstration program performs linear regression of genotype vs.
gene expression phenotypes (eQTLs), making use of ADAM Parquet Genotype file format as input, and Spark
as a scalable engine to execute hundreds of millions of statistical tests - making use of multiple processors
and scaling linearly across a cluster such as amazon EC2

In statistical genetics, associations tests are performed between genotype and phenotype.  In the case of a continuous trait such as height, linear regression can be used to associate the dosage (zero,1, or two copies) of an alternative allele at a position in the genome with the quantitative trait.  Over the past decade it has become possible to measure simultaneously thousands of molecular gene expression phenotypes, the amount of a gene transcript made by a cells (or a cultured cell line) derived from a specific person, and to associate that molecular phenotype with genetic variation within a sample population.   Thes studies are often termed (eQTL) for expression quantitative trait loci studies.

In this demonstration program, genotypes are formated into the efficient ADAM genotype format using Parquet and then Spark is used to perform hundreds of millions of linear regression tests.  

The genetic association test here is equally applicable to whole body QTLs like height, BMI, blood pressure, eQTLs were chosen as they sheet amount of computation requires a scalable solution.  Another area where Spark could be applied isthe pairwise and greater interaction among variants, which quickly also produces a combinatorial explosion of tests.

Note: demonstrated here is a way to efficiently parallelize these statistical tests using Spark, however scientific interpretation requires adjustment for the many millions of tests performed.  Corrections need to be made to ascertain signfificance based on prior hypothesis or otherwise the number of tests need to limited - often first (cis-acting) eQTLs are considered where the variant is in or near the gene for which expression is being tested for association.  An adaptive machine learning approach also using Spark may be a good option to narrow this search space, or to adaptively expand the search based on network analysis.

# Source Data and References

Datasets analyzed here are derived from those at:

(http://jungle.unige.ch/rnaseq_CEU60/)

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

To run from the base of this cloned repository using example chr22 and chr2 data in the data/ directory:

```
YOUR_SPARK_HOME/bin/spark-submit /home/paschall/learn/spark/spark120/spark-1.2.0-bin-hadoop2.4/bin/spark-submit   \
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

######Analyzing ~78,000 variants from chr2  (*Times in Minutes*)
|             | 32 cores | 16 cores | 8 cores | local - quadcore HT  |
| ----------- | -------- |:--------:|:-------:|:---------------------:|
| 100 pheno   | 1.5      | 1.92     | 1.96    | 2.02                  |
| 1000 pheno  | 3.9      | 5.7      | 10.4    | 14.1                  |
| 5000 pheno  | 13.9     | 23.29    | 47.1    | pending               |
| 10000 pheno | 27.7     | pending  | 94      | 135.1                 |



#Credits
Inspirations, including structure of this README from:
(https://github.com/nfergu/popstrat)

Keep watching the exciting work of [Big Data Genomics](http://bdgenomics.org)

