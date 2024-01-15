# Google Dataflow Ethereum ETL Streaming

## Pre-requisites

1. Create a [Cloud Storage bucket](https://cloud.google.com/storage/docs/creating-buckets).

    ```shell
    $ gsutil mb gs://<your-bucket-name>/dataflow/templates
    ```

2. Create Artifact Registry

   ```shell
   $ gcloud artifacts repositories create dataflow \
   --repository-format=docker \
   --location=<your-location>

## Build

```shell
$ ./gradlew clean installDeploymentDist
```

## Deploy
```shell
$ gcloud dataflow flex-template build gs://<your-bucket-name>/dataflow/templates/ethereum-etl-streaming.json \
 --image-gcr-path "<your-location>-docker.pkg.dev/dataflow/ethereum-etl-streaming:latest" \
 --sdk-language "JAVA" \
 --flex-template-base-image JAVA11 \
 --metadata-file "metadata.json" \
 --jar "build/install/ethereum-etl-dataflow-java-template-deployment/ethereum-etl-dataflow-java-template-1.0-SNAPSHOT.jar" \
 --jar "$(ls -mx build/install/ethereum-etl-dataflow-java-template-deployment/libs/* | tr -d '\n' | sed 's. ..g')" \
 --env FLEX_TEMPLATE_JAVA_MAIN_CLASS="coop.ethereumclassic.etl.google.dataflow.EthereumEtlTransformer"
```

## Run
```shell
$ gcloud dataflow flex-template run etl-streaming \
--region <your-region> \
--template-file-gcs-location gs://<your-bucket-name>/dataflow/templates/ethereum-etl-streaming.json \
--parameters dataset="<your-dataset>"
```

# Cleanup
```shell
$ gcloud dataflow jobs list \
     --filter 'NAME:etl-streaming AND STATE=Running' \
     --format 'value(JOB_ID)' \
   | xargs gcloud dataflow jobs cancel

$ gsutil rm -r gs://<your-bucket-name>/dataflow/templates

$ gcloud artifacts repositories delete dataflow \
    --quiet \
    --location=<your-location>
```

Replace `<your-bucket-name>`, `<your-location>`, and `<your-dataset>` with your actual values.