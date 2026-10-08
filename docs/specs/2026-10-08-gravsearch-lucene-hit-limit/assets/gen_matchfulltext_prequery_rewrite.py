"""Generate matchFulltext prequeries rewritten like DEV-6864's SearchFulltextQuery (REWRITE 1a-1d), keeping the
Gravsearch outer shape (project, label, class VALUES, isDeleted, ORDER BY label).

Usage: gen_rw.py <out-prefix> <term> <limit> [--project IRI] [--classes IRI,IRI]
Writes <out-prefix>_{page,count}_rw.rq
"""
import argparse

p = argparse.ArgumentParser()
p.add_argument("out")
p.add_argument("term")
p.add_argument("limit")
p.add_argument("--project")
p.add_argument("--classes")
a = p.parse_args()

KB = "http://www.knora.org/ontology/knora-base#"
T = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
esc = a.term.replace("\\", "\\\\").replace('"', '\\"')

body = f"""{{
SELECT DISTINCT ?match WHERE {{ ?match <http://jena.apache.org/text#query> ("{esc}" {a.limit}) . }}
}}
OPTIONAL {{
?match <{KB}valueCreationDate> ?valueCreationDate .
FILTER NOT EXISTS {{ ?match {T} <{KB}LinkValue> . }}
FILTER NOT EXISTS {{ ?match {T} <{KB}ListValue> . }}
?containingRes ?prop ?match .
FILTER NOT EXISTS {{ ?match <{KB}isDeleted> true . }}
}}
OPTIONAL {{
?match {T} <{KB}ListNode> .
?match <{KB}hasSubListNode>* ?subNode .
?listVal <{KB}valueHasListNode> ?subNode .
?resWithListVal ?pred ?listVal .
FILTER NOT EXISTS {{ ?match <{KB}isDeleted> true . }}
}}
BIND(COALESCE(?containingRes, ?resWithListVal, ?match) AS ?mainRes)
?mainRes <{KB}creationDate> ?resourceCreationDate .
"""
if a.project:
    body += f"?mainRes <{KB}attachedToProject> <{a.project}> .\n"
body += "?mainRes <http://www.w3.org/2000/01/rdf-schema#label> ?label .\n"
if a.classes:
    vals = " ".join(f"<{c}>" for c in a.classes.split(","))
    body += f"VALUES ?mainRes__resTypes {{ {vals} }}\n?mainRes {T} ?mainRes__resTypes .\n"
body += f"FILTER NOT EXISTS {{ ?mainRes <{KB}isDeleted> true . }}\n"

open(f"{a.out}_page_rw.rq", "w").write(
    f"SELECT DISTINCT ?mainRes\nWHERE {{\n{body}}}\nGROUP BY ?mainRes ?label\nORDER BY ASC(?label) ASC(?mainRes)\nLIMIT 25\n")
open(f"{a.out}_count_rw.rq", "w").write(
    f"SELECT (COUNT(DISTINCT ?mainRes) AS ?count)\nWHERE {{\n{body}}}\nLIMIT 1\n")
print("written", a.out)
