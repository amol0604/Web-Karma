/*******************************************************************************
 * Copyright 2014 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/
package edu.isi.karma.kr2rml.template;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.isi.karma.rep.HNodePath;
import edu.isi.karma.rep.Node;
import edu.isi.karma.rep.Row;

public class SinglyAnchoredTemplateTermSetPopulatorPlan extends
		TemplateTermSetPopulatorPlan {
	
	Map<ColumnTemplateTerm, TemplateTermSetPopulatorPlan> nestedPlans;
	public SinglyAnchoredTemplateTermSetPopulatorPlan(Map<ColumnTemplateTerm, HNodePath> termToPath,
			LinkedList<ColumnTemplateTerm> columnTerms,
			List<ColumnTemplateTerm> comparisonTerms)
	{
		super();
		this.comparisonTerms = comparisonTerms;
		this.columnTerms = columnTerms;
		this.termToPath = termToPath;
		generateComplicated();
	}

	private void generateComplicated() {
		if(columnTerms == null || columnTerms.isEmpty())
		{
			return;
		}
		nestedPlans = new HashMap<ColumnTemplateTerm, TemplateTermSetPopulatorPlan>();
		LinkedList<ColumnTemplateTerm> columnTermsLocal = new LinkedList<ColumnTemplateTerm>();
		columnTermsLocal.addAll(columnTerms);
		sortColumnTermsByHNodePathDepth(columnTermsLocal);
		Map<ColumnTemplateTerm, ColumnTemplateTerm> termsToTermDependentOn = generateTermsToTermDependentOn(columnTermsLocal, comparisonTerms);
		
		//Now we should have a column terms mapped to their closest affinity in the other group.
		Map<ColumnTemplateTerm, List<ColumnTemplateTerm>> otherToGenerate = new HashMap<ColumnTemplateTerm, List<ColumnTemplateTerm>>();
		List<ColumnTemplateTerm> independentNestedTerms = new LinkedList<ColumnTemplateTerm>();
		for(Entry<ColumnTemplateTerm, ColumnTemplateTerm> termToTermDependentOn : termsToTermDependentOn.entrySet())
		{
			ColumnTemplateTerm dependentTerm = termToTermDependentOn.getValue();
			ColumnTemplateTerm currentTerm = termToTermDependentOn.getKey();
			if(null != dependentTerm)
			{
				if(!otherToGenerate.containsKey(dependentTerm))
				{
					otherToGenerate.put(dependentTerm, new LinkedList<ColumnTemplateTerm>());
				}
				List<ColumnTemplateTerm> nestedTerms = otherToGenerate.get(dependentTerm);
				nestedTerms.add(currentTerm);
			}
			else
			{
				independentNestedTerms.add(currentTerm);
			}
		}
		for(Entry<ColumnTemplateTerm, List<ColumnTemplateTerm>> other : otherToGenerate.entrySet())
		{
			TemplateTermSetPopulatorWorker parentWorker = new TemplateTermSetPopulatorWorker(other.getKey(), termToPath.get(other.getKey()), null);
			TemplateTermSetPopulatorPlan plan = new TemplateTermSetPopulatorPlan(termToPath, other.getValue(), parentWorker);
			nestedPlans.put(other.getKey(), plan);
		}
		
		Map<ColumnTemplateTerm, ColumnTemplateTerm> parentTermsToTermDependentOn = new HashMap<ColumnTemplateTerm, ColumnTemplateTerm>();
		for(ColumnTemplateTerm independentTerm : independentNestedTerms)
		{
			parentTermsToTermDependentOn.put(independentTerm, null);
		}
		this.generateWorkers(parentTermsToTermDependentOn);
		this.findFirstWorker();
	}
	
	public Map<PopulatedTemplateTermSet, List<PartiallyPopulatedTermSet>> execute(Row topRow, List<PopulatedTemplateTermSet> anchors)
	{
		Map<PopulatedTemplateTermSet, List<PartiallyPopulatedTermSet>> results = new HashMap<PopulatedTemplateTermSet, List<PartiallyPopulatedTermSet>>();
		if(columnTerms == null || columnTerms.isEmpty())
		{
			for(PopulatedTemplateTermSet anchor: anchors)
			{
				List<PartiallyPopulatedTermSet> references = new LinkedList<PartiallyPopulatedTermSet>();
				references.add(new PartiallyPopulatedTermSet());
				results.put(anchor, references);
			}
			return results;
		}
		
		List<PartiallyPopulatedTermSet> independentResults = this.execute(topRow);
		
		for(PopulatedTemplateTermSet anchor: anchors)
		{
			List<List<PartiallyPopulatedTermSet>> toMerge = new LinkedList<List<PartiallyPopulatedTermSet>>();
			if(!independentResults.isEmpty())
			{
				toMerge.add(independentResults);
			}
			Map<ColumnTemplateTerm, Node> references = anchor.getReferences();
			for(Entry<ColumnTemplateTerm, Node> reference : references.entrySet())
			{
				TemplateTermSetPopulatorPlan plan = nestedPlans.get(reference.getKey());
				if(plan != null)
				{
					toMerge.add(plan.executeComplicated(topRow, reference.getValue()));
				}
			}
			
			results.put(anchor, TemplateTermSetPopulatorWorker.combinePartialResults(toMerge));
		}
		return results;
	}
}
