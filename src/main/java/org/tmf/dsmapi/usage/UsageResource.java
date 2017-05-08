package org.tmf.dsmapi.usage;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.codehaus.jackson.node.ObjectNode;
import org.tmf.dsmapi.commons.exceptions.BadUsageException;
import org.tmf.dsmapi.commons.exceptions.UnknownResourceException;
import org.tmf.dsmapi.commons.jaxrs.PATCH;
import org.tmf.dsmapi.commons.utils.Jackson;
import org.tmf.dsmapi.commons.utils.URIParser;
import org.tmf.dsmapi.usage.model.Usage;
import org.tmf.dsmapi.usage.event.UsageEventPublisherLocal;
import org.tmf.dsmapi.usage.event.UsageEventFacade;

@Stateless
@Path("/usageManagement/v2/usage")
public class UsageResource {

    @EJB
    UsageFacade usageFacade;
    @EJB
    UsageEventFacade eventFacade;
    @EJB
    UsageEventPublisherLocal publisher;

    public UsageResource() {
    }

    /**
     * Test purpose only
     */
    @POST
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response create(Usage entity, @Context UriInfo info) throws BadUsageException, UnknownResourceException {
        usageFacade.checkCreation(entity);
        usageFacade.create(entity);
        entity.setHref(info.getAbsolutePath()+ "/" + Long.toString(entity.getId()));
        usageFacade.edit(entity);
        publisher.createNotification(entity, new Date());
        // 201
        Response response = Response.status(Response.Status.CREATED).entity(entity).build();
        return response;
    }

    @GET
    @Produces({"application/json"})
    public Response find(@Context UriInfo info) throws BadUsageException {

        // search queryParameters
        MultivaluedMap<String, String> queryParameters = info.getQueryParameters();

        Map<String, List<String>> mutableMap = new HashMap();
        Map<String, String> charactFilter = new HashMap<>();

        queryParameters.entrySet().stream().forEach((e) -> {
            String[] parsedFilter = e.getKey().split("\\.");

            if(parsedFilter.length == 2 &&
                    parsedFilter[0].equals("usageCharacteristic") && e.getValue().size() == 1) {

                // Process filters based on characteristics value in a different map
                // usageCharacteristics.charName=charValue
                charactFilter.put(parsedFilter[1], e.getValue().get(0));

            } else {
                mutableMap.put(e.getKey(), e.getValue());
            }
        });

        // fields to filter view
        Set<String> fieldSet = URIParser.getFieldsSelection(mutableMap);

        Set<Usage> resultList = findByCriteria(mutableMap);
        if(!charactFilter.isEmpty()) {
            resultList = usageFacade.filterUsageCharacteristics(resultList, charactFilter);
        }

        Response response;
        if (fieldSet.isEmpty() || fieldSet.contains(URIParser.ALL_FIELDS)) {
            response = Response.ok(resultList).build();
        } else {
            fieldSet.add(URIParser.ID_FIELD);
            List<ObjectNode> nodeList = Jackson.createNodes(resultList, fieldSet);
            response = Response.ok(nodeList).build();
        }
        return response;
    }

    // return Set of unique elements to avoid List with same elements in case of join
    private Set<Usage> findByCriteria(Map<String, List<String>> criteria) throws BadUsageException {

        List<Usage> resultList;
        Set<Usage> result;

        if (criteria != null && !criteria.isEmpty()) {
            resultList = usageFacade.findByCriteria(criteria, Usage.class);
        } else {
            resultList = usageFacade.findAll();
        }

        if (resultList == null) {
            result = new LinkedHashSet<>();
        } else {
            result = new LinkedHashSet<>(resultList);
        }

        return result;
    }


    @GET
    @Path("{id}")
    @Produces({"application/json"})
    public Response get(@PathParam("id") long id, @Context UriInfo info) throws UnknownResourceException {

        // search queryParameters
        MultivaluedMap<String, String> queryParameters = info.getQueryParameters();

        Map<String, List<String>> mutableMap = new HashMap();
        for (Map.Entry<String, List<String>> e : queryParameters.entrySet()) {
            mutableMap.put(e.getKey(), e.getValue());
        }

        // fields to filter view
        Set<String> fieldSet = URIParser.getFieldsSelection(mutableMap);

        Usage usage = usageFacade.find(id);
        Response response;
       
        // If the result list (list of bills) is not empty, it conains only 1 unique bill
        if (usage != null) {
            // 200
            if (fieldSet.isEmpty() || fieldSet.contains(URIParser.ALL_FIELDS)) {
                response = Response.ok(usage).build();
            } else {
                fieldSet.add(URIParser.ID_FIELD);
                ObjectNode node = Jackson.createNode(usage, fieldSet);
                response = Response.ok(node).build();
            }
        } else {
            // 404 not found
            response = Response.status(Response.Status.NOT_FOUND).build();
        }
        return response;
    }

    @PATCH
    @Path("{id}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response patch(@PathParam("id") long id, Usage partialUsage) throws BadUsageException, UnknownResourceException {
        Response response = null;
        Usage currentProduct = usageFacade.patchAttributs(id, partialUsage);
        
        // 200 OK + location
        response = Response.status(Response.Status.OK).entity(currentProduct).build();

        return response;
    }

}
