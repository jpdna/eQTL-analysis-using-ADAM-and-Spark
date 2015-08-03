# Introduction
Analysis of genotypes and gene expression phenotype (eQTLs) using ADAM and Spark

This demonstration program performs linear regression of genotype vs.
gene expression phenotypes (eQTLs), making use of ADAM Parquet Genotype file format as input, and Spark
as a scalable engine to execute hundreds of millions of statistical tests - making use of multiple processors
and scaling linearly across a cluster such as amazon EC2

# Source Data and References

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

