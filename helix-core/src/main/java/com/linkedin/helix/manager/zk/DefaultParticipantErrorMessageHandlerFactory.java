package com.linkedin.helix.manager.zk;


import java.util.Arrays;

import org.apache.log4j.Logger;

import com.linkedin.helix.HelixException;
import com.linkedin.helix.HelixManager;
import com.linkedin.helix.NotificationContext;
import com.linkedin.helix.messaging.handling.HelixTaskResult;
import com.linkedin.helix.messaging.handling.MessageHandler;
import com.linkedin.helix.messaging.handling.MessageHandlerFactory;
import com.linkedin.helix.model.Message;

/**
 * DefaultParticipantErrorMessageHandlerFactory works on controller side.
 * When the participant detects a critical error, it will send the PARTICIPANT_ERROR_REPORT
 * Message to the controller, specifying whether it want to disable the instance or
 * disable the partition. The controller have a chance to do whatever make sense at that point,
 * and then disable the corresponding partition or the instance. More configs per resource will
 * be added to customize the controller behavior.
 * */
public class DefaultParticipantErrorMessageHandlerFactory implements
  MessageHandlerFactory
{
  public enum ActionOnError
  {
    DISABLE_PARTITION, DISABLE_RESOURCE, DISABLE_INSTANCE
  }

  public static final String ACTIONKEY = "ActionOnError";

  private static Logger _logger = Logger
    .getLogger(DefaultParticipantErrorMessageHandlerFactory.class);
  final HelixManager _manager;

  public DefaultParticipantErrorMessageHandlerFactory(HelixManager manager)
  {
    _manager = manager;
  }

  public static class DefaultParticipantErrorMessageHandler extends MessageHandler
  {
    final HelixManager _manager;
    public DefaultParticipantErrorMessageHandler(Message message,
        NotificationContext context,  HelixManager manager)
    {
       super(message, context);
       _manager = manager;
    }

    @Override
    public HelixTaskResult handleMessage() throws InterruptedException
    {
      HelixTaskResult result = new HelixTaskResult();
      result.setSuccess(true);
      // TODO : consider unify this with StatsAggregationStage.executeAlertActions()
      try
      {
        ActionOnError actionOnError
          = ActionOnError.valueOf(_message.getRecord().getSimpleField(ACTIONKEY));

        if(actionOnError == ActionOnError.DISABLE_INSTANCE)
        {
          _manager.getClusterManagmentTool().enableInstance(_manager.getClusterName(), _message.getMsgSrc(), false);
          _logger.info("Instance " + _message.getMsgSrc() + " disabled");
        }
        else if(actionOnError == ActionOnError.DISABLE_PARTITION)
        {
          _manager.getClusterManagmentTool().enablePartition(false, _manager.getClusterName(), _message.getMsgSrc(),
              _message.getResourceName(), Arrays.asList( _message.getPartitionName()));
          _logger.info("partition " + _message.getPartitionName() + " disabled");
        }
        else if (actionOnError == ActionOnError.DISABLE_RESOURCE)
        {
          // NOT IMPLEMENTED, or we can disable all partitions
          //_manager.getClusterManagmentTool().en(_manager.getClusterName(), _manager.getInstanceName(),
          //    _message.getResourceName(), _message.getPartitionName(), false);
          _logger.info("resource " + _message.getResourceName() + " disabled");
        }
      }
      catch(Exception e)
      {
        _logger.error("", e);
        result.setSuccess(false);
        result.setException(e);
      }
      return result;
    }

    @Override
    public void onError(Exception e, ErrorCode code, ErrorType type)
    {
      _logger.error("Message handling pipeline get an exception. MsgId:"
          + _message.getMsgId(), e);
    }

  }

  @Override
  public MessageHandler createHandler(Message message,
      NotificationContext context)
  {
    String type = message.getMsgType();

    if(!type.equals(getMessageType()))
    {
      throw new HelixException("Unexpected msg type for message "+message.getMsgId()
          +" type:" + message.getMsgType());
    }

    return new DefaultParticipantErrorMessageHandler(message, context, _manager);
  }

  @Override
  public String getMessageType()
  {
    return Message.MessageType.PARTICIPANT_ERROR_REPORT.toString();
  }

  @Override
  public void reset()
  {

  }

}
