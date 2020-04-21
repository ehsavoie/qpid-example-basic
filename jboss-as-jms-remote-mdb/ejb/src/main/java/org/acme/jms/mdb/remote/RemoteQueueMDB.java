/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the 
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.jms.mdb.remote;

import org.jboss.ejb3.annotation.ResourceAdapter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.naming.InitialContext;

import org.jboss.logging.Logger;

/**
 * Created with IntelliJ IDEA.
 * User: tomr
 * Date: 14/11/2013
 * Time: 08:47
 * To change this template use File | Settings | File Templates.
 */
@MessageDriven(name = "RemoteQueueMDB", activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
    @ActivationConfigProperty(propertyName = "destination", propertyValue = "${remote.in.queue.fqn}"),
    @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge"),
    @ActivationConfigProperty(propertyName = "connectionFactory", propertyValue = "${qpid.cf}"),
    @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "${max.session}"),
    @ActivationConfigProperty(propertyName = "user", propertyValue = "${user.name}"),
    @ActivationConfigProperty(propertyName = "password", propertyValue = "${user.password}")
}, mappedName = "java:/jms/qpid/queue/testQueue")
@ResourceAdapter("${ra.bind.name}")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RemoteQueueMDB implements MessageListener {

    private final static Logger LOG = Logger.getLogger(RemoteQueueMDB.class);
    private static AtomicInteger mdbCnt = new AtomicInteger(0);
    private int msgCnt = 0;
    private int mdbID = 0;
    private TextMessage txtMsg = null;

    @Resource(name = "${external.context}")
    private InitialContext exteranlContext;

    @Resource(name = "${remote.out.queue.fqn}")
    private Queue outQueue;

    //@Resource(name = "${jca.connection.factory}")
    @Resource(lookup = "${qpid.cf}")
    private QueueConnectionFactory qcf;

    //@Resource
    private MessageDrivenContext ctx;

    private String outQueueName = "outQueue";

    public RemoteQueueMDB() {

        String className = this.getClass().getName();

        if (className.equals("org.acme.jms.mdb.remote.RemoteQueueMDB")) {

            mdbID = mdbCnt.getAndIncrement();

        }

        LOG.infof("MDB[%d] MDB class %s created", mdbID, className);

    }

    /**
     * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
     */
    @Override
    public void onMessage(Message message) {
        try (QueueConnection queueConnection = qcf.createQueueConnection();
                QueueSession queueSession = queueConnection.createQueueSession(true, Session.SESSION_TRANSACTED);
                QueueSender queueSender = queueSession.createSender(outQueue)) {
            if (LOG.isDebugEnabled()) {
                LOG.debugf("MDB[%d] Message unique value = %s", mdbID, message.getStringProperty(JMSMessageProperties.UNIQUE_VALUE));
            }
            if (message instanceof TextMessage) {
                txtMsg = (TextMessage) message;
                if (LOG.isDebugEnabled()) {
                    LOG.debugf("MDB[%d] Received Message[%s]: with text '%s'.", mdbID, txtMsg.toString(), txtMsg.getText());
                }
                msgCnt++;
                long delay = txtMsg.getLongProperty(JMSMessageProperties.MESSAGE_CONSUMER_DELAY);
                if (delay != 0) {
                    Thread.sleep(5000);
                }
                boolean throwException = txtMsg.getBooleanProperty(JMSMessageProperties.MESSAGE_THROW_EXCEPTION);
                if (throwException) {
                    throw new RuntimeException("This is a dummy exception.");
                }
                queueSender.send(message);
                msgCnt++;
                queueSession.commit();
            } else {
                LOG.warnf("MDB[%d] Message of wrong type: %s", mdbID, message.getClass().getName());
            }
        } catch (JMSException jmsException) {
            ctx.setRollbackOnly();
            LOG.errorf(jmsException, "MDB[%d] Got error while excuting onMessage() method.", mdbID);
            throw new RuntimeException(jmsException);
        } catch (InterruptedException interruptedException) {
            ctx.setRollbackOnly();
            LOG.errorf(interruptedException, "MDB[%d] Caught InterruptedException while in sleep.", mdbID);
            throw new RuntimeException(interruptedException);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debugf("MDB[%d] JMS resources closed.", mdbID);
        }

    }

    @PreDestroy
    public void printStats() {
        LOG.infof("MDB[%d] Processed %d messages.", mdbID, msgCnt);
        LOG.infof("MDB[%d] Closing.", mdbID);
        if (LOG.isDebugEnabled()) {
            LOG.debugf("MDB[%d] MDB count is ", mdbID, mdbCnt.get());
        }
        mdbCnt.decrementAndGet();
    }

    @PostConstruct
    public void init() {
        LOG.infof("MDB[%d] created.", mdbID);
    }

}
