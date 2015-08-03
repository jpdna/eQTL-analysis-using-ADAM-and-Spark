# Introduction
Analysis of genotypes and gene expression phenotype (eQTLs) using ADAM and Spark

This demonstration program performs linear regression of genotype vs.
gene expression phenotypes (eQTLs), making use of ADAM Parquet Genotype file format as input, and Spark
as a scalable engine to execute hundreds of millions of statistical tests - making use of multiple processors
and scaling linearly across a cluster such as amazon EC2

In statistical genetics, associations tests are performed between genotype and phenotype.  In the case of a continuous trait such as height, linear regression can be used to associate the dosage (zero,1, or two copies) of an alternative allele at a position in the genome with the quantitative trait.  Over the past decade it has become possible to measure simultaneously thousands of molecular gene expression phenotypes, the amount of a gene transcript made by a cells (or a cultured cell line) derived from a specific person, and to associate that molecular phenotype with genetic variation within a sample population.   Thes studies are often termed (eQTL) for expression quantitative trait loci studies.

In this demonstration program, genotypes are formated into the efficient ADAM genotype format using Parquet and then Spark is used to perform hundreds of millions of linear regression tests.  

Note: demonstrated here is a way efficiently parallelize these tests using Spark - next corrections need to be made to ascertain signfificance based of prior hypothesis or otherwise limit the number of tests considered.  An adaptive machine learning approach also using Spark may be a good option to narrow this search space.

# Source Data and References

Datasets analyzed here are derived from those at:
(http://jungle.unige.ch/rnaseq_CEU60/)
Cited in the paper:
(http://www.nature.com/nature/journal/v464/n7289/pdf/nature08903.pdf)

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

#Credits
Inspirations, including structure of this README from:
(https://github.com/nfergu/popstrat)

Keep watching the exciting work of [Big Data Genomics](http://bdgenomics.org)

