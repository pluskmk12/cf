
package ch.ethz.inf.vs.californium.rd.resources;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.ethz.inf.vs.californium.coap.CoAP.ResponseCode;
import ch.ethz.inf.vs.californium.coap.MediaTypeRegistry;
import ch.ethz.inf.vs.californium.server.resources.CoapExchange;
import ch.ethz.inf.vs.californium.server.resources.Resource;
import ch.ethz.inf.vs.californium.server.resources.ResourceBase;

public class RDTagTopResource extends ResourceBase {

	private RDResource rdResource = null;

	public RDTagTopResource(RDResource rd) {
		this("tags", rd);
	}

	public RDTagTopResource(String resourceIdentifier, RDResource rd) {
		super(resourceIdentifier);
		this.rdResource = rd;

	}

	@Override
	public void handleGET(CoapExchange exchange) {
		List<String> query = exchange.getRequestOptions().getURIQueries();
		HashMap<String, String> tags = new HashMap<String, String>();
		String resourcePath = "";
		String ep = "";
		for (String q : query) {
			if (q.startsWith("ep=")) {
				ep = q.substring(q.indexOf("=") + 1).replace("\"", "");
			} else if (q.startsWith("res=")) {
				resourcePath = q.substring(q.indexOf("=") + 1).replace("\"", "");
			} else {
				String tag = q;
				if (tag.contains("=")) {
					tags.put(tag.substring(0, tag.indexOf("=")).toLowerCase().trim(), tag.substring(tag.indexOf("=") + 1).toLowerCase().replace("\"", "").trim());
				} else {
					tags.put(tag.toLowerCase().trim(), "true");
				}
			}
		}
		if (resourcePath.startsWith("/")) {
			resourcePath = resourcePath.substring(1);
		}
		if (!ep.isEmpty() && !resourcePath.isEmpty() && tags.isEmpty()) {
			// Get Tags of resource
			RDTagResource target = null;
			for (Resource res : rdResource.getChildren()) {
				if (res.getClass() == RDNodeResource.class) {
					if (((RDNodeResource) res).getEndpointIdentifier().equals(ep)) {
						if (getSubResource(res, resourcePath) != null && getSubResource(res, resourcePath).getClass() == RDTagResource.class) {
							target = (RDTagResource) getSubResource(res, resourcePath);
							break;
						}
					}
				}
			}
			if (target != null) {
				String payload = "";
				HashMap<String, String> tagMap = target.getTags();
				for (String tag : tagMap.keySet()) {
					payload += tag + "=" + tagMap.get(tag) + ",";
				}
				if (payload.endsWith(",")) {
					payload = payload.substring(0, payload.length() - 1);
				}
				exchange.respond(ResponseCode.CONTENT, payload);
				
			} else {
				exchange.respond(ResponseCode.NOT_FOUND);
				
			}
		} else if (!tags.isEmpty() && ep.isEmpty() && resourcePath.isEmpty()) {
			// Get resource with specified Tags
			Set<RDTagResource> result = getSubResourceWithTags(tags, rdResource);
			if (result.isEmpty()) {
				exchange.respond(ResponseCode.NOT_FOUND);
				
			} else {
				StringBuilder linkFormat = new StringBuilder();
				for (RDTagResource res : result) {
					linkFormat.append("<" + res.getParentNode().getContext());
					linkFormat.append(res.getPath().substring(res.getParentNode().getPath().length()));
					linkFormat.append(">");
					
					//TODO
					linkFormat.append(";TODO");
//					
//					for (LinkAttribute attrib : res.getAttributes()) {
//						linkFormat.append(';');
//						linkFormat.append(attrib.serialize());
//
//					}
					linkFormat.append(",");
				}
				linkFormat.deleteCharAt(linkFormat.length() - 1);
				
				exchange.respond(ResponseCode.CONTENT,linkFormat.toString(), MediaTypeRegistry.APPLICATION_LINK_FORMAT);
			}
		} else {
			exchange.respond(ResponseCode.BAD_REQUEST);
		}
	}

	@Override
	public void handlePUT(CoapExchange exchange) {
		String resourcePath = "";
		String ep = "";
		Set<Resource> targets = new HashSet<Resource>();

		HashMap<String, String> payloadMap = new HashMap<String, String>();
		String[] splittedPayload = exchange.getRequestText().split("\n");
		List<String> query = exchange.getRequestOptions().getURIQueries();

		for (String q: query) {
			if (q.startsWith("ep=")) {
				ep = q.substring(q.indexOf("=") + 1).replace("\"", "");
			}
			if (q.startsWith("res=")) {
				resourcePath = q.substring(q.indexOf("=") + 1).replace("\"", "");
			}
		}
		for (String line : splittedPayload) {
			if (line.contains(":")) {
				if (line.charAt(line.indexOf(":") + 1) == '{' && line.endsWith("}")) {
					payloadMap.put(line.substring(0, line.indexOf(":")), line.substring(line.indexOf(":") + 2, line.length() - 1));
				} else {
					exchange.respond(ResponseCode.BAD_REQUEST);
					return;
				}
			} else {
				exchange.respond(ResponseCode.BAD_REQUEST);
				return;
			}
		}
		System.out.println(ep);
		System.out.println(resourcePath);
		if ((!payloadMap.containsKey("add") && !payloadMap.containsKey("delete")) || ep.isEmpty()) {
			exchange.respond(ResponseCode.BAD_REQUEST);
			return;
		}
		if (!resourcePath.isEmpty()) {
			if (resourcePath.startsWith("/")) {
				resourcePath = resourcePath.substring(1);
			}
			for (Resource res : rdResource.getChildren()) {
				if (res.getClass() == RDNodeResource.class) {
					if (((RDNodeResource) res).getEndpointIdentifier().equals(ep)) {
						targets.add(getSubResource(res, resourcePath));
						break;
					}
				}
			}
		} else {
			LinkedList<Resource> todo = new LinkedList<Resource>();
			for (Resource res : rdResource.getChildren()) {
				if (res.getClass() == RDNodeResource.class) {
					if (((RDNodeResource) res).getEndpointIdentifier().equals(ep)) {
						todo.add(res);
						break;
					}
				}
			}
			while (!todo.isEmpty()) {
				Resource current = todo.pop();
				if (current.getAttributes().containsAttribute("ep")) {
					targets.add(current);
				}
				for (Resource child : current.getChildren()) {
					todo.add(child);
				}
			}

		}
		if (targets.isEmpty()) {
			exchange.respond(ResponseCode.BAD_REQUEST);
			return;
		}

		for (Resource target : targets) {
			if (target.getClass() != RDTagResource.class) {
				continue;
			}

			if (payloadMap.containsKey("delete")) {
				HashSet<String> tags = new HashSet<String>();
				for (String tag : payloadMap.get("delete").split(",")) {
					if (tag.contains("=")) {
						tags.add(tag.substring(0, tag.indexOf("=")).toLowerCase().trim());
					} else {
						tags.add(tag.toLowerCase().trim());
					}
				}
				((RDTagResource) target).removeMultipleTags(tags);
			}
			if (payloadMap.containsKey("add")) {
				HashMap<String, String> tags = new HashMap<String, String>();
				for (String tag : payloadMap.get("add").split(",")) {
					if (tag.contains("=")) {
						tags.put(tag.substring(0, tag.indexOf("=")).toLowerCase().trim(), tag.substring(tag.indexOf("=") + 1).toLowerCase().replace("\"", "").trim());
					} else {
						tags.put(tag.toLowerCase().trim(), "true");
					}

				}
				((RDTagResource) target).addMultipleTags(tags);
			}
		}
		exchange.respond(ResponseCode.CHANGED);

	}
	
	private Resource getSubResource(Resource root, String path) {
		if (path == null || path.isEmpty()) {
			return root;
		}

		// find root for absolute path
		if (path.startsWith("/")) {
			while (root.getParent() != null) {
				root = root.getParent();
			}
			path = path.equals("/") ? null : path.substring(1);
			return getSubResource(root, path);
		}

		int pos = path.indexOf('/');
		String head = null;
		String tail = null;

		// note: "some/resource/" addresses a resource "" under "resource"
		if (pos != -1) {
			head = path.substring(0, pos);
			tail = path.substring(pos + 1);
		} else {
			head = path;
		}

		Resource sub = root.getChild(head);

		if (sub != null) {
			return getSubResource(sub, tail);
		} else {
			return null;
		}
	}

	private Set<RDTagResource> getSubResourceWithTags(HashMap<String, String> tags, Resource start) {
		LinkedList<Resource> toDo = new LinkedList<Resource>();
		toDo.add(start);
		HashSet<RDTagResource> result = new HashSet<RDTagResource>();
		while (!toDo.isEmpty()) {
			Resource current = toDo.pop();
			if (current.getClass() == RDTagResource.class) {
				if (((RDTagResource) current).containsMultipleTags(tags)) {
					result.add((RDTagResource) current);
				}
			}
			toDo.addAll(current.getChildren());
		}
		return result;
	}
}
