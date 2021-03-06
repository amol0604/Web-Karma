package edu.isi.karma.controller.command.alignment;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.util.bloom.Key;
import org.apache.hadoop.util.hash.Hash;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandType;
import edu.isi.karma.controller.update.AbstractUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.er.helper.CloneTableUtils;
import edu.isi.karma.er.helper.TripleStoreUtil;
import edu.isi.karma.kr2rml.KR2RMLBloomFilter;
import edu.isi.karma.modeling.alignment.AlignmentManager;
import edu.isi.karma.rep.HNode;
import edu.isi.karma.rep.Node;
import edu.isi.karma.rep.RepFactory;
import edu.isi.karma.rep.Row;
import edu.isi.karma.rep.Table;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.Workspace;
import edu.isi.karma.rep.alignment.Label;
import edu.isi.karma.rep.metadata.WorksheetProperties.Property;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.webserver.KarmaException;

public class SearchForDataToAugmentIncomingCommand extends Command{
	private static final Logger LOG = LoggerFactory.getLogger(SearchForDataToAugmentIncomingCommand.class);
	private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");
	private String tripleStoreUrl;
	private String context;
	private String nodeUri;
	private String worksheetId;
	private String columnUri;
	private final Integer limit = 100;
	public SearchForDataToAugmentIncomingCommand(String id, String url, String context, String nodeUri, String worksheetId, String columnUri) {
		super(id);
		this.tripleStoreUrl = url;
		this.context = context;
		this.nodeUri = nodeUri;
		this.worksheetId = worksheetId;
		this.columnUri = columnUri;
	}

	@Override
	public String getCommandName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getTitle() {
		return "Search For Data To Augment";
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.notInHistory;
	}

	@Override
	public UpdateContainer doIt(Workspace workspace) throws CommandException {

		Worksheet worksheet = workspace.getWorksheet(worksheetId);
		RepFactory factory = workspace.getFactory();
		TripleStoreUtil util = new TripleStoreUtil();
		HashMap<String, List<String>> result = null;
		nodeUri = nodeUri.trim();
		Map<String, Label> parents = workspace.getOntologyManager().getSuperClasses(nodeUri, true);
		Set<String> classes = new HashSet<String>(parents.keySet());
		classes.add(nodeUri);
		StringBuilder builder = new StringBuilder();
		nodeUri = builder.append("<").append(nodeUri).append(">").toString();
		try {
			result = util.getPredicatesForParentTriplesMapsWithSameClass(tripleStoreUrl, context, classes);
		} catch (KarmaException e) {
			LOG.error("Unable to find predicates for triples maps with same class as: " + nodeUri, e);
		}
		final JSONArray array = new JSONArray();
		List<JSONObject> objects = new ArrayList<JSONObject>();
		List<String> concatenatedPredicateObjectMapsList = result.get("refObjectMaps");
		List<String> predicates = result.get("predicates");
		List<String> otherClasses = result.get("otherClasses");
		Iterator<String> concatenatedPredicateObjectMapsListItr = concatenatedPredicateObjectMapsList.iterator();
		Iterator<String> predicatesItr = predicates.iterator();
		Iterator<String> otherClassesItr = otherClasses.iterator();
		String hNodeId = FetchHNodeIdFromAlignmentCommand.gethNodeId(AlignmentManager.Instance().constructAlignmentId(workspace.getId(), worksheetId), columnUri);
		HNode hnode = factory.getHNode(hNodeId);
		List<Table> dataTables = new ArrayList<Table>();
		CloneTableUtils.getDatatable(worksheet.getDataTable(), factory.getHTable(hnode.getHTableId()), dataTables);
		KR2RMLBloomFilter uris = new KR2RMLBloomFilter(KR2RMLBloomFilter.defaultVectorSize, KR2RMLBloomFilter.defaultnbHash, Hash.JENKINS_HASH);
		Set<String> uriSet = new HashSet<String>();
		for(Table t : dataTables) {
			for(Row r : t.getRows(0, t.getNumRows())) {
				Node n = r.getNode(hNodeId);
				if(n != null && n.getValue() != null && !n.getValue().isEmptyValue() && n.getValue().asString() != null && !n.getValue().asString().trim().isEmpty() ) {
					String value = n.getValue().asString().trim().replace(" ", "");;
					builder = new StringBuilder();
					String baseURI = worksheet.getMetadataContainer().getWorksheetProperties().getPropertyValue(Property.baseURI);
					try {
						URI uri = new URI(value);
						if (!uri.isAbsolute() && baseURI != null) {
							value = baseURI + value;
						}
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
					}
//					n.setValue(value, n.getStatus(), factory);
					value = builder.append("<").append(value).append(">").toString(); //String builder
					uriSet.add(value);
					uris.add(new Key(value.getBytes(UTF8_CHARSET)));
				}
			}
		}
		Set<String> maps = new HashSet<String>();
		Map<String, String> bloomfilterMapping = new HashMap<String, String>();
		try{
			for (String concatenatedPredicateObjectMaps : concatenatedPredicateObjectMapsList) {
				List<String> predicateObjectMaps = new ArrayList<String>(Arrays.asList(concatenatedPredicateObjectMaps.split(",")));
				maps.addAll(predicateObjectMaps);
				if (maps.size() > limit) {
					bloomfilterMapping.putAll(util.getBloomFiltersForMaps(tripleStoreUrl, context, maps));
					maps = new HashSet<String>();
				}
			}
			if (maps.size() > 0)
				bloomfilterMapping.putAll(util.getBloomFiltersForMaps(tripleStoreUrl, context, maps));
		} catch (KarmaException e1) {
			e1.printStackTrace();
		}
		while(concatenatedPredicateObjectMapsListItr.hasNext() && predicatesItr.hasNext() && otherClassesItr.hasNext())
		{
			String concatenatedPredicateObjectMaps = concatenatedPredicateObjectMapsListItr.next();
			List<String> predicateObjectMaps = new ArrayList<String>(Arrays.asList(concatenatedPredicateObjectMaps.split(",")));
			String predicate =  predicatesItr.next();
			String otherClass = otherClassesItr.next();
			try {
				KR2RMLBloomFilter intersectionBF = new KR2RMLBloomFilter(KR2RMLBloomFilter.defaultVectorSize, KR2RMLBloomFilter.defaultnbHash, Hash.JENKINS_HASH);
				for (String triplemap : predicateObjectMaps) {
					String serializedBloomFilter = bloomfilterMapping.get(triplemap);
					if (serializedBloomFilter != null) {
						KR2RMLBloomFilter bf = new KR2RMLBloomFilter();
						bf.populateFromCompressedAndBase64EncodedString(serializedBloomFilter);
						intersectionBF.or(bf);
					}
				}
				intersectionBF.and(uris);
				int estimate = intersectionBF.estimateNumberOfHashedValues();
				JSONObject obj = new JSONObject();
				obj.put("predicate", predicate);
				obj.put("otherClass", otherClass);
				obj.put("estimate", estimate);
				obj.put("incoming", "true");
				//array.put(obj);
				objects.add(obj);
			} catch (Exception e) {
				LOG.error("Unable to process bloom filter: " + e.getMessage());
			}
		}
		Collections.sort(objects, new Comparator<JSONObject>() {

			@Override
			public int compare(JSONObject a, JSONObject b) {
				return b.getInt("estimate") - a.getInt("estimate");
			}
		});
		for (JSONObject obj : objects) {
			array.put(obj);
		}
		return new UpdateContainer(new AbstractUpdate() {

			@Override
			public void generateJson(String prefix, PrintWriter pw, VWorkspace vWorkspace) {
				System.out.println(array.toString());
				pw.print(array.toString());
			}
		});
	}

	@Override
	public UpdateContainer undoIt(Workspace workspace) {
		return null;
	}

}
