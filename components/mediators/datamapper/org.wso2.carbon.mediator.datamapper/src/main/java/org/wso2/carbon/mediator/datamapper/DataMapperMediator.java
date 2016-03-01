/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.mediator.datamapper;

import org.apache.avro.generic.GenericRecord;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.Value;
import org.apache.synapse.util.AXIOMUtils;
import org.wso2.datamapper.engine.core.MappingHandler;
import org.wso2.datamapper.engine.core.MappingResourceLoader;
import org.wso2.datamapper.engine.datatypes.InputOutputDataTypes;
import org.wso2.datamapper.engine.datatypes.OutputWriter;
import org.wso2.datamapper.engine.datatypes.OutputWriterFactory;
import org.wso2.datamapper.engine.inputAdapters.InputDataReaderAdapter;
import org.wso2.datamapper.engine.inputAdapters.InputReaderFactory;

import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


/**
 * By using the input schema, output schema and mapping configuration,
 * DataMapperMediator generates the output required by the next mediator for the
 * input received by the previous mediator.
 */
public class DataMapperMediator extends AbstractMediator implements ManagedLifecycle {

    private Value mappingConfigurationKey = null;
    private Value inputSchemaKey = null;
    private Value outputSchemaKey = null;
    private String inputType = null;
    private String outputType = null;
    private UUID id = null;
    private static final Log log = LogFactory.getLog(DataMapperMediator.class);

    /**
     * Gets the key which is used to pick the mapping configuration from the
     * registry
     *
     * @return the key which is used to pick the mapping configuration from the
     * registry
     */
    public Value getMappingConfigurationKey() {
        return mappingConfigurationKey;
    }

    /**
     * Sets the registry key in order to pick the mapping configuration
     *
     * @param dataMapperconfigKey registry key for the mapping configuration
     */
    public void setMappingConfigurationKey(Value dataMapperconfigKey) {
        this.mappingConfigurationKey = dataMapperconfigKey;
    }

    /**
     * Gets the key which is used to pick the input schema from the
     * registry
     *
     * @return the key which is used to pick the input schema from the
     * registry
     */
    public Value getInputSchemaKey() {
        return inputSchemaKey;
    }

    /**
     * Sets the registry key in order to pick the input schema
     *
     * @param dataMapperInSchemaKey registry key for the input schema
     */
    public void setInputSchemaKey(Value dataMapperInSchemaKey) {
        this.inputSchemaKey = dataMapperInSchemaKey;
    }

    /**
     * Gets the key which is used to pick the output schema from the
     * registry
     *
     * @return the key which is used to pick the output schema from the
     * registry
     */
    public Value getOutputSchemaKey() {
        return outputSchemaKey;
    }

    /**
     * Sets the registry key in order to pick the output schema
     *
     * @param dataMapperOutSchemaKey registry key for the output schema
     */
    public void setOutputSchemaKey(Value dataMapperOutSchemaKey) {
        this.outputSchemaKey = dataMapperOutSchemaKey;
    }

    /**
     * Gets the input data type
     *
     * @return the input data type
     */
    public String getInputType() {
        return inputType;
    }

    /**
     * Sets the input data type
     *
     * @param type the input data type
     */
    public void setInputType(String type) {
        this.inputType = type;
    }

    /**
     * Gets the output data type
     *
     * @return the output data type
     */
    public String getOutputType() {
        return outputType;
    }

    /**
     * Sets the output data type
     *
     * @param type the output data type
     */
    public void setOutputType(String type) {
        this.outputType = type;
    }

    /**
     * Gets the unique ID for the DataMapperMediator instance
     *
     * @return the unique ID
     */
    public String getUniqueID() {
        String uuid = id.toString();
        return uuid;
    }

    /**
     * Sets the unique ID for the DataMapperMediator instance
     *
     * @param id the unique ID
     */
    public void setUniqueID(UUID id) {
        this.id = id;
    }

    /**
     * Get the values from the message context to do the data mapping
     *
     * @param synCtx current message for the mediation
     * @return true if mediation happened successfully else false.
     */
    @Override
    public boolean mediate(MessageContext synCtx) {

        SynapseLog synLog = getLog(synCtx);

        if (synCtx.getEnvironment().isDebugEnabled()) {
            if (super.divertMediationRoute(synCtx)) {
                return true;
            }
        }
        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : DataMapper mediator");
            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message :" + synCtx.getEnvelope());
            }
        }

        String configKey = mappingConfigurationKey.evaluateValue(synCtx);
        String inSchemaKey = inputSchemaKey.evaluateValue(synCtx);
        String outSchemaKey = outputSchemaKey.evaluateValue(synCtx);

        //checks the availability of the inputs for data mapping
        if (!(StringUtils.isNotEmpty(configKey)
                && StringUtils.isNotEmpty(inSchemaKey) && StringUtils
                .isNotEmpty(outSchemaKey))) {
            handleException("DataMapper mediator : Invalid configurations", synCtx);
        } else {
            try {
                // Does message conversion and gives the final result
                transform(synCtx, configKey, inSchemaKey,
                        outSchemaKey, inputType, outputType, getUniqueID());

            } catch (SynapseException e) {
                handleException("DataMapper mediator mediation failed", e, synCtx);
            } catch (IOException e) {
                handleException("DataMapper mediator mediation failed", e, synCtx);
            }
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("End : DataMapper mediator");
            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }
        return true;
    }

    /**
     * Does message conversion and gives the output message as the final result
     *
     * @param synCtx       the message synCtx
     * @param configkey    registry location of the mapping configuration
     * @param inSchemaKey  registry location of the input schema
     * @param outSchemaKey registry location of the output schema
     * @param inputType    input data type
     * @param outputType   output data type
     * @param uuid         unique ID for the DataMapperMediator instance
     * @throws SynapseException
     * @throws IOException
     */
    private void transform(MessageContext synCtx, String configkey,
                           String inSchemaKey, String outSchemaKey, String inputType,
                           String outputType, String uuid) throws SynapseException,
            IOException {

        MappingResourceLoader mappingResourceLoader = null;
        OMElement outputMessage = null;

        try {
            // mapping resources needed to get the final output
            mappingResourceLoader = CacheResources.getCachedResources(synCtx,
                    configkey, inSchemaKey, outSchemaKey, uuid);


            InputDataReaderAdapter inputReader = InputReaderFactory.getReader(inputType);

            InputStream inputStream = getInputStream(synCtx, inputType);

            GenericRecord result = MappingHandler.doMap(inputStream, mappingResourceLoader, inputReader);

            // Output message
            OutputWriter writer = OutputWriterFactory.getWriter(outputType);
            outputMessage = writer.getOutputMessage(outputType, result);

            if (outputMessage != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Output message received ");
                }
                // Use to create the SOAP message
                if (outputMessage != null) {
                    OMElement firstChild = outputMessage.getFirstElement();
                    if (firstChild != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Contains a first child");
                        }
                        QName resultQName = firstChild.getQName();
                        // TODO use XPath
                        if (resultQName.getLocalPart().equals("Envelope")
                                && (resultQName
                                .getNamespaceURI()
                                .equals(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI) || resultQName
                                .getNamespaceURI()
                                .equals(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI))) {
                            SOAPEnvelope soapEnvelope = AXIOMUtils
                                    .getSOAPEnvFromOM(outputMessage
                                            .getFirstElement());
                            if (soapEnvelope != null) {
                                try {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Valid Envelope");
                                    }
                                    synCtx.setEnvelope(soapEnvelope);
                                } catch (AxisFault axisFault) {
                                    handleException("Invalid Envelope",
                                            axisFault, synCtx);
                                }
                            }
                        } else {
                            synCtx.getEnvelope().getBody().getFirstElement()
                                    .detach();
                            synCtx.getEnvelope().getBody()
                                    .addChild(outputMessage);

                        }
                    } else {
                        synCtx.getEnvelope().getBody().getFirstElement()
                                .detach();
                        synCtx.getEnvelope().getBody().addChild(outputMessage);
                    }
                }
            }
        } catch (Exception e) {
            handleException("Mapping failed", e, synCtx);
        }

    }

    private InputStream getInputStream(MessageContext context, String inputType) {

        InputStream inputStream = null;
        switch (InputOutputDataTypes.DataType.fromString(inputType)) {
            case XML:
            case CSV:
                inputStream = new ByteArrayInputStream(
                        context.getEnvelope().getBody().getFirstElement().toString().getBytes(StandardCharsets.UTF_8));
                break;
            case JSON:
                org.apache.axis2.context.MessageContext a2mc = ((Axis2MessageContext) context).getAxis2MessageContext();
                if (JsonUtil.hasAJsonPayload(a2mc)) {
                    inputStream = JsonUtil.getJsonPayload(a2mc);
                }
                break;
            default:
                inputStream = new ByteArrayInputStream(
                        context.getEnvelope().getBody().getFirstElement().toString().getBytes(StandardCharsets.UTF_8));
                break;
        }
        return inputStream;
    }


    /**
     * State that DataMapperMediator interacts with the message context
     *
     * @return true if the DataMapperMediator is intending to interact with the
     * message context
     */
    @Override
    public boolean isContentAware() {
        return true;
    }

    @Override
    public void init(SynapseEnvironment se) {

    }

    /**
     * destroy the generated unique ID for the DataMapperMediator instance
     */
    @Override
    public void destroy() {
        if (id != null) {
            setUniqueID(id);
        }
    }

}