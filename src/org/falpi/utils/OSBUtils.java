package org.falpi.utils;

import java.util.Calendar;
import java.util.Iterator;
import java.lang.reflect.Method;
import javax.xml.namespace.QName;

import com.bea.wli.config.Ref;
import com.bea.wli.config.component.NotFoundException;
import com.bea.wli.reporting.EndpointType;
import com.bea.wli.reporting.FaultType;
import com.bea.wli.reporting.MessageContextType;
import com.bea.wli.reporting.MessagecontextDocument;
import com.bea.wli.reporting.OriginType;
import com.bea.wli.reporting.ReportingDataManager;
import com.bea.wli.reporting.ServiceType;
import com.bea.wli.reporting.StateType;
import com.bea.wli.reporting.TransportType;
import com.bea.wli.sb.ALSBConfigService;
import com.bea.wli.sb.context.InboundEndpoint;
import com.bea.wli.sb.context.MessageContextThreadLocal;
import com.bea.wli.sb.context.OutboundEndpoint;
import com.bea.wli.sb.pipeline.PipelineException;
import com.bea.wli.sb.resources.schema.SchemaRepository;
import com.bea.wli.sb.resources.service.BusinessServiceRepository;
import com.bea.wli.sb.resources.service.CommonServiceRepository;
import com.bea.wli.sb.resources.service.ProxyRepository;
import com.bea.wli.sb.resources.wsdl.EffectiveWSDL;
import com.bea.wli.sb.resources.wsdl.IncompleteEffectiveWSDLException;
import com.bea.wli.sb.resources.wsdl.WsdlRepository;
import com.bea.wli.sb.resources.xml.XmlRepository;
import com.bea.wli.sb.sources.Source;
import com.bea.wli.sb.sources.SourceUtils;
import com.bea.wli.sb.sources.TransformException;
import com.bea.wli.security.encryption.PBE_EncryptionService;
import com.bea.xbean.xb.xsdschema.SchemaDocument;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

import org.falpi.utils.XMLUtils;

public class OSBUtils {

   // ##############################################################################################
   // Sottoclasse per la gestione del message context
   // ##############################################################################################

   public static class MessageContext {

      // ==================================================================================================================================
      // Acquisisce $messageId
      // ==================================================================================================================================
      public static String getMessageID() throws PipelineException {
         return MessageContextThreadLocal.get().getMessageId();
      }

      // ==================================================================================================================================
      // Acquisisce variabile $operation
      // ==================================================================================================================================
      public static String getOperation() throws PipelineException {
         return MessageContextThreadLocal.get().getOperation();
      }

      // ==================================================================================================================================
      // Acquisisce la variable $header
      // ==================================================================================================================================
      public static XmlObject getHeader() throws PipelineException {
         return MessageContextThreadLocal.get().getHeader();
      }

      // ==================================================================================================================================
      // Imposta la variable $header 
      // ==================================================================================================================================
      public static void setHeader(XmlObject ObjHeader) throws PipelineException {
         MessageContextThreadLocal.get().setHeader(ObjHeader);
      }

      // ==================================================================================================================================
      // Acquisisce la variable $body
      // ==================================================================================================================================
      public static XmlObject getBody() throws PipelineException, TransformException {
         return SourceUtils.toXML(MessageContextThreadLocal.get().getBody());
      }

      // ==================================================================================================================================
      // Imposta la variabile $body
      // ==================================================================================================================================
      public static void setBody(XmlObject ObjBody) throws PipelineException {
         MessageContextThreadLocal.get().setBody((Source) ObjBody);
      }

      // ==================================================================================================================================
      // Acquisisce payload
      // ==================================================================================================================================
      public static XmlObject getPayload() throws PipelineException, TransformException {
         return SourceUtils.toXML(MessageContextThreadLocal.get().getPayload());
      }

      // ==================================================================================================================================
      // Imposta il payload
      // ==================================================================================================================================
      public static void setPayload(XmlObject ObjPayload) throws PipelineException {
         MessageContextThreadLocal.get().setPayload((Source) ObjPayload);
      }

      // ==================================================================================================================================
      // Acquisisce la variable $attachments
      // ==================================================================================================================================
      public static XmlObject getAttachments() throws PipelineException {
         return MessageContextThreadLocal.get().getAttachments();
      }

      // ==================================================================================================================================
      // Acquisisce la variable $fault
      // ==================================================================================================================================
      public static XmlObject getFault() throws PipelineException {
         return MessageContextThreadLocal.get().getFault();
      }

      // ==================================================================================================================================
      // Acquisisce la variable INBOUND dal message context corrente
      // ==================================================================================================================================
      public static XmlObject getInbound() throws PipelineException {
         InboundEndpoint ObjInbound = MessageContextThreadLocal.get().getInbound();
         return (ObjInbound != null) ? (ObjInbound.toXML()) : (null);
      }

      // ==================================================================================================================================
      // Acquisisce la variable $outbound
      // ==================================================================================================================================
      public static XmlObject getOutbound() throws PipelineException {
         OutboundEndpoint ObjOutbound = MessageContextThreadLocal.get().getOutbound();
         return (ObjOutbound != null) ? (ObjOutbound.toXML()) : (null);
      }
      
      // ==================================================================================================================================
      // Acquisisce le variabili
      // ==================================================================================================================================
      public static Iterator<String> getVariableNames() throws PipelineException {
         return MessageContextThreadLocal.get().getVariableNames();
      }      

      public static Object getVariableValue(String StrVariableName) throws PipelineException {
         return MessageContextThreadLocal.get().getVariableValue(StrVariableName);
      }      
   }
   
   // ##############################################################################################
   // Metodi statici della classe primaria
   // ##############################################################################################
   
   // ==================================================================================================================================
   // Mappa remote user su local user mediante un service account di mapping
   // ==================================================================================================================================
   public static String getMappedLocalUser(String StrServiceAccountPath, String StrRemoteUser) throws Exception {
      return XMLUtils.getTextValue(getResource("ServiceAccount", StrServiceAccountPath,true),"//*:user-mapping[@remote-user='" + StrRemoteUser + "']/@local-user");
   }

   // ==================================================================================================================================
   // Mappa remote user su local user mediante un service account di mapping
   // ==================================================================================================================================
   public static XmlObject getMappedRemoteUser(String StrServiceAccountPath, String StrLocalUser) throws Exception {
      return getResource("ServiceAccount", StrServiceAccountPath,true).selectPath("//*:remote-user[*:username/text()='" + StrLocalUser + "']")[0];
   }

   // ==================================================================================================================================
   // Acquisisce risorsa OSB in formato XML
   // ==================================================================================================================================
   public static XmlObject getResource(String StrResourceType, String StrResourcePath) throws Exception {
      return getResource(StrResourceType,StrResourcePath,true);
   }
   
   public static XmlObject getResource(String StrResourceType, String StrResourcePath, Boolean BolStripComments) throws Exception {

      // Prepara variabili
      XmlObject ObjResource = null;
      XmlOptions ObjOptions = new XmlOptions();

      // Se è richiesto di rimuovere i commenti setta opzione specifica
      if (BolStripComments) ObjOptions.setLoadStripComments();

      // Prepara riferimento alla risorsa
      Ref ObjResourceRef = getResourceRef(StrResourceType, StrResourcePath);

      // Acquisisce la risorsa con l'interfaccia 
      if (StrResourceType.equals("XML")) {
         ObjResource = XmlObject.Factory.parse(XmlRepository.get().getEntry(ObjResourceRef).xmlText(ObjOptions));
      } else if (StrResourceType.equals("WSDL")) {
         ObjResource = XmlObject.Factory.parse(WsdlRepository.get().getEntry(ObjResourceRef).xmlText(ObjOptions));
      } else if (StrResourceType.equals("XMLSchema")) {
         ObjResource = XmlObject.Factory.parse(SchemaRepository.get().getEntry(ObjResourceRef).xmlText(ObjOptions));
      } else if (StrResourceType.equals("ProxyService")) {
         ObjResource = XmlObject.Factory.parse(ProxyRepository.get().getEntry(ObjResourceRef).xmlText(ObjOptions));
      } else if (StrResourceType.equals("BusinessService")) {
         ObjResource = XmlObject.Factory.parse(BusinessServiceRepository.get().getEntry(ObjResourceRef).xmlText(ObjOptions));
      } else if (StrResourceType.equals("ServiceAccount")) {

         // Acquisisce la risorsa con l'interfaccia generale che restituisce l'istanza nella classe specifica  
         Object ObjResourceData = ALSBConfigService.get().getConfigService().getConfigContext().getResourceData(ObjResourceRef,true);

         // Converte in XML usando la reflection per bypassare i controlli di accesso (comune a tutti i tre tipi di service account)
         Method ObjMethod = ObjResourceData.getClass().getDeclaredMethod("toExportedForm", PBE_EncryptionService.class);
         ObjMethod.setAccessible(true);
         
         // Imposta opzione per definire un elemento radice appropriato (altrimenti si genera un xml-fragment problematico)
         ObjOptions.setSaveSyntheticDocumentElement(new QName("serviceAccount"));
         
         // Riesegue il parsing dell'XML 
         ObjResource = XmlObject.Factory.parse(XmlObject.class.cast(ObjMethod.invoke(ObjResourceData,new Object[]{ null })).xmlText(ObjOptions));
      }

      return ObjResource;
   }
   
   // ==================================================================================================================================
   // Acquisisce puntamento a risorsa OSB
   // ==================================================================================================================================
   public static Ref getResourceRef(String StrResourcePath) {
      return Ref.parseGlobalName(StrResourcePath);
   }
   
   public static Ref getResourceRef(String StrResourceType, String StrResourcePath) {
      return new com.bea.wli.config.Ref(StrResourceType, Ref.getNames(StrResourcePath));
   }

   // ==================================================================================================================================
   // Acquisisce il WSDL di un servizio
   // ==================================================================================================================================
   public static EffectiveWSDL getServiceInterface(String StrServicePath) throws NotFoundException {
      return CommonServiceRepository.getEffectiveWSDL(getResourceRef(StrServicePath));
   }

   // ==================================================================================================================================
   // Acquisisce gli Schema associati ad un servizio
   // ==================================================================================================================================
   @SuppressWarnings("deprecation")
   public static SchemaDocument[] getServiceSchemas(String StrServicePath) throws NotFoundException, IncompleteEffectiveWSDLException {
      return getServiceInterface(StrServicePath).getWsdl().getTypes().getSchemaArrayWithoutImport();
   }
   
   // ==================================================================================================================================
   // Crea un messaggio di reporting
   // ==================================================================================================================================
   public static void createReportingMessage(XmlObject ObjPayload, String StrStateName, String StrNodeName,
                                             String StrPipelineName, String StrStageName, String StrMessageLabels,
                                             String StrInboundServiceName, String StrInboundServiceURI, String StrInboundOperation, 
                                             String StrOutboundServiceName,String StrOutboundServiceURI, String StrOutboundOperation, 
                                             String StrErrorCode, String StrErrorReason, XmlObject ObjErrorDetails) {

      // Prepara le componenti di base del messaggio di reporting
      ReportingDataManager ObjReportingManager = ReportingDataManager.getManager();
      MessagecontextDocument ObjMessageDocument = MessagecontextDocument.Factory.newInstance();
      MessageContextType ObjMessage = ObjMessageDocument.addNewMessagecontext();

      ObjMessage.setTimestamp(Calendar.getInstance());
      ObjMessage.setLabels(StrMessageLabels);

      // Prepara le informazioni sul contesto del messaggio
      OriginType ObjMessageOrigin = ObjMessage.addNewOrigin();
      ObjMessageOrigin.setState(StateType.Enum.forString(StrStateName));
      ObjMessageOrigin.setNode(StrNodeName);
      ObjMessageOrigin.setStage(StrStageName);
      ObjMessageOrigin.setPipeline(StrPipelineName);

      // Prepara le componenti dell'INBOUND
      EndpointType ObjMessageInbound = ObjMessage.addNewInboundEndpoint();
      TransportType ObjMessageInboundTransport = ObjMessageInbound.addNewTransport();
      ObjMessageInbound.setName(StrInboundServiceName);

      if (StrInboundServiceURI != null)
         ObjMessageInboundTransport.setUri(StrInboundServiceURI);

      if (StrInboundOperation != null) {
         ServiceType ObjMessageInboundService = ObjMessageInbound.addNewService();
         ObjMessageInboundService.setOperation(StrInboundOperation);
      }

      // Se fornite, prepara le componenti dell'OUTBOUND
      if (StrOutboundServiceName != null) {

         EndpointType ObjMessageOutbound = ObjMessage.addNewOutboundEndpoint();
         TransportType ObjMessageOutboundTransport = ObjMessageOutbound.addNewTransport();
         ObjMessageOutbound.setName(StrOutboundServiceName);

         if (StrOutboundServiceURI != null)
            ObjMessageOutboundTransport.setUri(StrOutboundServiceURI);

         if (StrOutboundOperation != null) {
            ServiceType ObjMessageOutboundService = ObjMessageOutbound.addNewService();
            ObjMessageOutboundService.setOperation(StrOutboundOperation);
         }
      }

      // Se fornite, prepara le componenti dell'ERROR
      if (StrErrorCode != null) {
         FaultType ObjMessageFault = ObjMessage.addNewFault();
         ObjMessageFault.setErrorCode(StrErrorCode);

         if (StrErrorReason != null)
            ObjMessageFault.setReason(StrErrorReason);

         if (ObjErrorDetails != null)
            ObjMessageFault.setDetails(ObjErrorDetails);
      }

      try {
         // Accoda il messaggio di reporting provider
         ObjReportingManager.handle(ObjMessageDocument, ObjPayload);

      } catch (Exception ObjException) {
         throw new RuntimeException(ObjException.toString());
      }
   }
}
