# Payload Size Limit Mediator

A custom mediator implementation to limit the payload based on size in WSO2 API Manager.

> The current implementation version is only tested in the WSO2 API Manager v2.2.0

### Use Case

This custom (sample) class mediator is implemented to restrict the Payload based on the Size limit. This mediator can be used on both the Request and Response flows to restrict the Payloads which are larger than the preferred values.

Please find the detailed instructions [below](#instructions) on how to configure and use at the runtime in your environments.

### Instructions

- Clone or download the Repo/Project and execute the following command from the root directory to build the project

    ```sh
    mvn clean package
    ```

- Place the built artifact `payload-size-limit-x.x.x.jar` inside the `<APIM>/repository/components/lib` directory and restart the server
- Create a `Global In/Out Sequence` or API Specific mediation sequence (please go through the Note mentioned in [Customizations & Points](#customizations--points)) and add the following Class Mediator definition and the mediators to filter according to the provided size limit.
  
  > You can find a sample [mediation sequence](/sequences/mediation-sequence.xml) inside the `/sequences` directory of this repo.

    ```xml
    <!-- defining the payload size limit class mediator in the sequence with the respective size limit in MB unit -->
    <!-- the class mediator will return a boolean value with the property named "payload-size-too-large" to specify whether the message    is too large or not -->
    <class name="com.sample.mediator.PayloadSizeLimitMediator">
        <!-- the size limit has been configured to 10MB limit, change this according to your requirement -->
        <property name="sizeLimit" value="10" />
        <!-- specifying the API name. You can use either $ctx:API_NAME or $ctx:SYNAPSE_REST_API -->
        <property name="apiName" expression="$ctx:API_NAME" />
        <!-- specify the direction flow of the execution whether in flow or out flow -->
        <property name="flowDirection" value="In flow" />
    </class>

    <!-- a custom log mediator to log the property returned by class mediator -->
    <log level="custom">
        <property name="is-payload-larger" expression="get-property('payload-size-too-large')" />
    </log>

    <!-- filter and send a custom error response based on the class mediator output property -->
    <!-- if the property 'payload-size-too-large' is set to true means, that the payload size is larger than the defined value -->
    <filter source="get-property('payload-size-too-large')" regex="true">
        <then>
            <!-- a custom log mediator -->
            <log level="custom">
                <property name="message" value="The payload size is larger than the specified limit" />
            </log>

            <!-- payload factory mediator to specify custom response -->
            <payloadFactory media-type="json">
                <format>
                    {
                        "status": $1,
                        "message": "$2"
                    }
                </format>
                <args>
                    <arg value="413" />
                    <arg value="payload is too large" />
                </args>
            </payloadFactory>

            <!-- change the HTTP status code. Below is set to 400 status code -->
            <property name="HTTP_SC" value="413" scope="axis2" />
            <!-- response back to the client with application/json format -->
            <property name="messageType" value="application/json" scope="axis2" />

            <!-- respond back to the client if the payload size is too large with the above-mentioned custom response -->
            <respond />
        </then>
        <else>
            <!-- a custom log mediator to log the flow -->
            <log level="custom">
                <property name="message" value="The payload size is not larger than specified limit" />
            </log>
        </else>
    </filter>
    ```

### Customizations & Points

> **Note:**
>
> The current implementation first checks whether the Message has been built prior to the execution. If the message has been built by any prior mediators then, the mediator will not work as expected on filtering the payloads.
>
> Hence, it is advised to place the mediator prior to other `content-aware` mediators in the mediation sequence to avoid flaws.

- In the `Payload Factory Mediator`, you can define any custom error responses that has to be sent to the client if the payload size meets the limit

    ```xml
    <!-- payload factory mediator to specify custom response -->
    <payloadFactory media-type="json">
        <format>
            {
                "status": $1,
                "message": "$2"
            }
        </format>
        <args>
            <arg value="413" />
            <arg value="payload is too large" />
        </args>
    </payloadFactory>
    ```

- You can set the Payload limit size from the mediation sequence itself using the `sizeLimit` property when defining the class mediator. The following is an extract explaining the configuration of the Payload size limit

    ```xml
    <class name="com.sample.mediator.PayloadSizeLimitMediator">
        <!-- the size limit has been configured to 10MB limit, change this according to your requirement -->
        <property name="sizeLimit" value="10" />
    </class>
    ```

### Contributions

Pull Requests, Issues, Improvments, and etc. are welcomed

## License

Licensed Under [Apache 2.0](LICENSE)
