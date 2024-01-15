package coop.ethereumclassic.etl.google.dataflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.services.bigquery.model.TableRow;
import org.apache.beam.runners.dataflow.options.DataflowPipelineWorkerPoolOptions;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Flatten;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EthereumEtlTransformer {

  final private static Map<String, TupleTag<TableRow>> outputTags =
      Stream.of("block", "transaction", "log", "token_transfer", "trace", "contract", "token")
          .collect(Collectors.toMap(type -> type, type -> new TupleTag<>(type){}));

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .registerModule(new JodaModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

  public static void main(String[] args) {
    Options options = PipelineOptionsFactory.fromArgs(args).withValidation().as(Options.class);
    Pipeline pipeline = Pipeline.create(options);

    PCollectionList<PubsubMessage> inputs = PCollectionList.empty(pipeline);
    String dataset = options.getDataset();
    for (String entity : outputTags.keySet()) {
      inputs = inputs.and(
          pipeline.apply(
              "Read" + capitalize(entity) + "Subscription",
              PubsubIO.readMessagesWithAttributes().fromSubscription(String.format("projects/%s/subscriptions/etl.%s", options.getProject(), entity + "s"))));
    }

    PCollection<PubsubMessage> readPubSubTopics = inputs.apply("Flatten", Flatten.pCollections());
    PCollection<ObjectNode> cleanedMessages = readPubSubTopics
        .apply("CleanUpMessage", ParDo.of(new DoFn<PubsubMessage, ObjectNode>() {
          @ProcessElement
          public void processElement(ProcessContext context) throws IOException {
            PubsubMessage message = context.element();
            ObjectNode mutableNode = MAPPER.readTree(message.getPayload()).deepCopy();
            mutableNode.remove("item_id");
            mutableNode.remove("item_timestamp");
            context.output(mutableNode);
          }
        }));
    TupleTag<TableRow> anyDefaultOutputTag = outputTags.remove(outputTags.keySet().stream().findAny().get());
    PCollectionTuple bigQueryRows = cleanedMessages
        .apply("ConvertJsonMessageToTableRow", ParDo.of(new DoFn<ObjectNode, TableRow>() {
          @ProcessElement
          public void processElement(ProcessContext context) throws IOException {
            ObjectNode jsonNode = context.element().deepCopy();
            if (jsonNode.has("type")) {
              JsonNode typeField = jsonNode.remove("type");
              TableRow tableRow = MAPPER.treeToValue(jsonNode, TableRow.class);
              String type = typeField.asText();
              context.output(outputTags.get(type), tableRow);
            }
          }
        }).withOutputTags(anyDefaultOutputTag, TupleTagList.of(List.copyOf(outputTags.values()))));
    outputTags.forEach((type, tag) ->
        bigQueryRows.get(tag)
            .apply("Write" + capitalize(type) + "Records", BigQueryIO.writeTableRows()
                .withoutValidation()
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER)
                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)
                .withExtendedErrorInfo()
                .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                .to(String.format("%s:%s.%s", options.getProject(), dataset, type + "s")
                )));

    pipeline.run();
  }

  public interface Options extends PipelineOptions, DataflowPipelineWorkerPoolOptions {
    String getDataset();

    void setDataset(String dataSet);
  }

  public static String capitalize(String str) {
    if (str == null || str.length() <= 1) return str;
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }
}
