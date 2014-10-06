/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.handlers.collection;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import com.mongodb.util.JSONParseException;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.json.metadata.InvalidMetadataException;
import com.softinstigate.restheart.json.metadata.Relationship;
import com.softinstigate.restheart.utils.ChannelReader;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.RequestHelper;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class PatchCollectionHandler extends PipedHttpHandler
{
    private static final Logger logger = LoggerFactory.getLogger(PatchCollectionHandler.class);
    
    /**
     * Creates a new instance of PatchCollectionHandler
     */
    public PatchCollectionHandler()
    {
        super(null);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getDBName().isEmpty())
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong request, db name cannot be empty");
            return;
        }
        
        if (context.getCollectionName().isEmpty() || context.getCollectionName().startsWith("@"))
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong request, collection name cannot be empty or start with @");
            return;
        }

        String _content = ChannelReader.read(exchange.getRequestChannel());

        DBObject content;

        try
        {
            content = (DBObject) JSON.parse(_content);
        }
        catch (JSONParseException ex)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "json data is invalid", ex);
            return;
        }
        
        if (content == null)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "no json data provided");
            return;
        }
        
        // cannot PUT an array
        if (content instanceof BasicDBList)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "json data cannot be an array");
            return;
        }
        
        if (content.containsField(Relationship.RELATIONSHIPS_ELEMENT_NAME))
        {
            try
            {
                Relationship.getFromJson(content);
            }
            catch(InvalidMetadataException ex)
            {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_ACCEPTABLE, "wrong relationships definition. " + ex.getMessage(), ex);
                return;
            }
        }
        
        ObjectId etag = RequestHelper.getUpdateEtag(exchange);
        
        if (etag == null)
        {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_CONFLICT, "the " + Headers.ETAG + " header must be provided");
            logger.warn("the {} header in required", Headers.ETAG);
            return;
        }
        
        int SC = CollectionDAO.upsertCollection(context.getDBName(), context.getCollectionName(), content, etag, true, true);
        
        ResponseHelper.endExchange(exchange, SC);
    }
}