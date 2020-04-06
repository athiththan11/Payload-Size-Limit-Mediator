package com.sample.mediator;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.Map;

import org.apache.axis2.Constants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.transport.nhttp.NhttpConstants;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.Pipe;
import org.apache.synapse.transport.passthru.util.RelayUtils;

public class PayloadSizeLimitMediator extends AbstractMediator {

    // default size limit is set to 10 MB
    private String sizeLimit = "10";
    private String apiName = "not available";
    private String flowDirection = "not available";
    private int statusCode = 202;

    private static final String PAYLOAD_SIZE_LIMIT_PROPERTY = "payload-size-too-large";

    private static DecimalFormat decimalFormat = new DecimalFormat("0.00");
    private static final Log logger = LogFactory.getLog(PayloadSizeLimitMediator.class);

    @Override
    public boolean mediate(MessageContext synapseMessageContext) {

        if (logger.isDebugEnabled()) {
            logger.debug("Mediate method execution started");
            logger.debug("API Name : " + apiName + " Direction Flow : " + flowDirection);
        }

        org.apache.axis2.context.MessageContext messageContext = ((Axis2MessageContext) synapseMessageContext)
                .getAxis2MessageContext();
        try {
            final Pipe pipe = (Pipe) messageContext.getProperty(PassThroughConstants.PASS_THROUGH_PIPE);

            if (messageContext.getProperty(Constants.Configuration.CONTENT_TYPE) != null) {

                if (logger.isDebugEnabled()) {
                    logger.debug("Content Type : " + messageContext.getProperty(Constants.Configuration.CONTENT_TYPE));
                    logger.debug("Message Builder Invoked : "
                            + (Boolean) messageContext.getProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED));
                }

                if (pipe != null && !Boolean.TRUE
                        .equals(messageContext.getProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED))) {

                    if (logger.isDebugEnabled()) {
                        logger.debug("Passthrough Pipe is not null and Message Builder is not invoked previously");
                    }

                    InputStream in = pipe.getInputStream();
                    Object httpStatusCode = messageContext.getProperty(NhttpConstants.HTTP_SC);
                    if (httpStatusCode instanceof Integer && httpStatusCode.equals(statusCode)) {

                        if (logger.isDebugEnabled()) {
                            logger.debug("HTTP Status Code is not null and equal to " + Integer.toString(statusCode));
                        }

                        if (in != null) {

                            if (logger.isDebugEnabled()) {
                                logger.debug("Inputstream of Passthrough Pipe is not null");
                            }

                            InputStream bis = new ReadOnlyBIS(in);
                            int c = bis.read();
                            if (c == -1) {

                                if (logger.isDebugEnabled()) {
                                    logger.debug("Buffered Input Stream reached the end and returned -1");
                                }

                                messageContext.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
                                messageContext.setProperty(PassThroughConstants.NO_ENTITY_BODY, Boolean.TRUE);
                                bis.close();
                                return true;
                            }
                            bis.reset();
                            in = bis;
                            bis.close();
                        }
                    }

                    Map headers = (Map) messageContext
                            .getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
                    if (headers.containsKey("Content-Length")) {

                        /**
                         * this block checks for the Content-Length header and based on the presence
                         * validates the payload size
                         */

                        if (logger.isDebugEnabled()) {
                            logger.debug("Content Length header is present");
                        }

                        double contentLength = Double.valueOf(String.valueOf(headers.get("Content-Length")))
                                / (1024 * 1024);
                        contentLength = Double.valueOf(decimalFormat.format(contentLength));

                        if (logger.isDebugEnabled()) {
                            logger.debug("Content Length : " + contentLength);
                        }

                        // if payload size is more than the specified size limit, set true to
                        // payload-size-too-large property in message context and print a warn in the
                        // carbon logs
                        if (contentLength > Integer.parseInt(sizeLimit)) {

                            if (logger.isDebugEnabled()) {
                                logger.debug("Content Length is greater than " + sizeLimit + " MB");
                            }

                            logger.warn("Cannot proceed further... The payload size is " + contentLength
                                    + " MB which is larger than " + sizeLimit + " MB for the API : " + apiName + " , Direction Flow : " + flowDirection);
                            synapseMessageContext.setProperty(PAYLOAD_SIZE_LIMIT_PROPERTY, true);
                            return true;
                        }
                    } else {

                        /**
                         * this block is executed when the Content-Length header is not presented and
                         * the payload is chunked. This block retrieves the size of the input stream and
                         * validates against the size limit mentioned
                         */

                        if (logger.isDebugEnabled()) {
                            logger.debug("Content Length header is not present");
                        }

                        // if payload size is more than the specified size limit, set true to
                        // payload-size-too-large property in the message context and print a warn in
                        // the carbon logs
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        IOUtils.copy(in, byteArrayOutputStream);
                        byteArrayOutputStream.flush();

                        // Open new InputStream using the recorded bytes and assign to in
                        in = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                        RelayUtils.builldMessage(messageContext, false, in);
                        double streamSizeInBytes = byteArrayOutputStream.size();
                        double streamSizeInMb = streamSizeInBytes / (1024 * 1024);
                        streamSizeInMb = Double.valueOf(decimalFormat.format(streamSizeInMb));

                        if (logger.isDebugEnabled()) {
                            logger.debug("Stream Size : " + streamSizeInMb);
                        }

                        if (streamSizeInMb > Integer.parseInt(sizeLimit)) {

                            if (logger.isDebugEnabled()) {
                                logger.debug("Stream Size is greater than " + sizeLimit + " MB");
                            }

                            logger.warn("Cannot proceed further... The payload size is " + streamSizeInMb
                                    + " MB which is larger than " + sizeLimit + " MB for the API : " + apiName + " , Direction Flow : " + flowDirection);
                            synapseMessageContext.setProperty(PAYLOAD_SIZE_LIMIT_PROPERTY, true);
                            return true;
                        }
                    }
                    return true;
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Content Type is not presented and the message is not build");
                }

                messageContext.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
                return true;
            }
        } catch (Exception e) {
            handleException("Error while logging the message content", e, synapseMessageContext);
        }
        return true;
    }

    // set the sizeLimit property
    public void setSizeLimit(String size) {
        sizeLimit = size;
    }

    // get the sizeLimit property
    public String getSizeLimit() {
        return sizeLimit;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getFlowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(String flowDirection) {
        this.flowDirection = flowDirection;
    }

    @Override
    public boolean isContentAware() {
        return false;
    }

    /**
     * An Un-closable, Read-Only, Reusable, BufferedInputStream
     */
    private static class ReadOnlyBIS extends BufferedInputStream {
        private static final String LOG_STREAM = "org.apache.synapse.transport.passthru.util.ReadOnlyStream";
        private static final Log logger = LogFactory.getLog(LOG_STREAM);

        public ReadOnlyBIS(InputStream inputStream) {
            super(inputStream);
            super.mark(Integer.MAX_VALUE);
            if (logger.isDebugEnabled()) {
                logger.debug("<init>");
            }
        }

        @Override
        public void close() throws IOException {
            super.reset();
            if (logger.isDebugEnabled()) {
                logger.debug("#close");
            }
        }

        @Override
        public synchronized void mark(int readlimit) {
            if (logger.isDebugEnabled()) {
                logger.debug("#mark");
            }
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public synchronized long skip(long n) {
            if (logger.isDebugEnabled()) {
                logger.debug("#skip");
            }
            return 0;
        }
    }
}
