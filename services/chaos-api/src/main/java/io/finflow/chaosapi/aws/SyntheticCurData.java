package io.finflow.chaosapi.aws;

import com.fasterxml.jackson.databind.ObjectMapper; //converts JSON text into java objects and vice versa
import io.finflow.chaosapi.aws.dto.AwsCurLineItem;  //it contains the mapping of JSON to these java variables
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;    //java logging tools - print formatted msgs
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;   //java tool to read raw data
import java.util.List;

/**
 * Memory cache for the mock AWS CUR data-
 *
 * This component reads the aws-cur-lineitems.json fixture exactly once when the application
 * starts up. It uses Spring's injected ObjectMapper to parse the JSON into an immutable list
 * of Java records and holds it in memory.
 *
 * Why -
 * By loading the data into RAM at startup via the PostConstruct annotation, the application
 * avoids slow file reading operations on every single API request. Exposing the data through
 * an unmodifiable list guarantees that downstream services cannot accidentally alter or
 * destroy the mock billing state while using it.
 */

@Component
public class SyntheticCurData{

    //creating a logger specifically for this file - when msg is printed at the terminal, this ame will be printed
    private static final Logger log = LoggerFactory.getLogger(SyntheticCurData.class);
    private static final String RESOURCE = "data/aws-cur-lineitems.json";
    private final ObjectMapper objectMapper;

    //lineItems will hold the actual data - initialised as empty list
    private List<AwsCurLineItem> lineItems = List.of();

    //constructor for DI
    public SyntheticCurData(ObjectMapper objectMapper){
        this.objectMapper = objectMapper;
    }

    @PostConstruct //
    void load() throws Exception{
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()){
            lineItems = List.of(objectMapper.readValue(in, AwsCurLineItem[].class));
            //readValue(in, ) reads the raw file stream
            //
        }
        log.info("[AWS-CHAOS] loaded {} synthetic CUR line items from {}",
                lineItems.size(), RESOURCE);
    }

    //call .all() and this class hands the locked, immutable list to the rest of the application
    public List<AwsCurLineItem> all() {
        return lineItems;
    }
}