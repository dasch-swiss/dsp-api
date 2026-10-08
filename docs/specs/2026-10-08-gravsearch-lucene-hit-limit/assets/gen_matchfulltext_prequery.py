"""Generate matchFulltext prequeries in the shape AbstractPrequeryGenerator emits (see trace 81a502a6…).

Usage: gen_ft.py <out-prefix> <term> [--project SHORTCODE] [--classes IRI,IRI]
Writes <out-prefix>_{page,count}_{unlim,lim}.rq
"""
import argparse

p = argparse.ArgumentParser()
p.add_argument("out")
p.add_argument("term")
p.add_argument("--project")
p.add_argument("--classes")
a = p.parse_args()

KB = "http://www.knora.org/ontology/knora-base#"
RDF_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
SUBCLASS = "<http://www.w3.org/2000/01/rdf-schema#subClassOf>"
DEL = f'FILTER NOT EXISTS {{\n ?{{v}} <{KB}isDeleted> "true"^^<http://www.w3.org/2001/XMLSchema#boolean> .\n}}'


def body(lucene_obj):
    m = "?match__matchFulltext"
    s = f"""{{
{m} <http://jena.apache.org/text#query> {lucene_obj} .
OPTIONAL {{
{m} {RDF_TYPE} ?valType__matchFulltext .
?valType__matchFulltext {SUBCLASS}* <{KB}Value> .
FILTER(((?valType__matchFulltext != <{KB}LinkValue>) && (?valType__matchFulltext != <{KB}ListValue>)))
?containingRes__matchFulltext ?prop__matchFulltext {m} .
?prop__matchFulltext <http://www.w3.org/2000/01/rdf-schema#subPropertyOf>* <{KB}hasValue> .
{DEL.replace('{v}', 'match__matchFulltext')}
}}
OPTIONAL {{
{m} {RDF_TYPE} <{KB}ListNode> .
{m} <{KB}hasSubListNode>* ?subNode__matchFulltext .
?listVal__matchFulltext <{KB}valueHasListNode> ?subNode__matchFulltext .
?resWithListVal__matchFulltext ?pred__matchFulltext ?listVal__matchFulltext .
{DEL.replace('{v}', 'match__matchFulltext')}
}}
BIND(COALESCE(?containingRes__matchFulltext, ?resWithListVal__matchFulltext, {m}) AS ?mainRes)
?mainRes {RDF_TYPE} ?resClass__matchFulltext .
?resClass__matchFulltext {SUBCLASS}* <{KB}Resource> .
}}
"""
    if a.project:
        proj = a.project if "://" in a.project else f"http://rdfh.ch/projects/{a.project}"
        s += f"?mainRes <{KB}attachedToProject> <{proj}> .\n"
    s += "?mainRes <http://www.w3.org/2000/01/rdf-schema#label> ?label .\n"
    if a.classes:
        vals = " ".join(f"<{c}>" for c in a.classes.split(","))
        s += f"VALUES ?mainRes__resTypes {{ {vals} }}\n?mainRes {RDF_TYPE} ?mainRes__resTypes .\n"
    s += DEL.replace("{v}", "mainRes") + "\n"
    return s


esc = a.term.replace("\\", "\\\\").replace('"', '\\"')
objs = {"unlim": f'"{esc}"^^<http://www.w3.org/2001/XMLSchema#string>', "lim": f'("{esc}" 1000000)'}
for k, o in objs.items():
    page = f"SELECT DISTINCT ?mainRes\nWHERE {{\n{body(o)}}}\nGROUP BY ?mainRes ?label\nORDER BY ASC(?label) ASC(?mainRes)\nLIMIT 25\n"
    count = f"SELECT DISTINCT (COUNT(DISTINCT ?mainRes) AS ?count)\nWHERE {{\n{body(o)}}}\nLIMIT 1\n"
    open(f"{a.out}_page_{k}.rq", "w").write(page)
    open(f"{a.out}_count_{k}.rq", "w").write(count)
print("written", a.out)
