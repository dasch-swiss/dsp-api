import sys
# gen.py <shape: classless|classed> <term> -> prints a Gravsearch count prequery in the golden shape
# (GravsearchToCountPrequeryTransformerE2ESpec__classlessMatchFulltext.txt), unscoped.
shape, term = sys.argv[1], sys.argv[2]
classes = [l.strip() for l in open("resclasses.csv").read().splitlines()[1:] if l.strip()]
if shape == "classed":
    classes = ["http://www.knora.org/ontology/0812/ekws#Object"]
values = " ".join(f"<{c}>" for c in sorted(classes))
KB = "http://www.knora.org/ontology/knora-base#"
RDFT = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
DEL = f'<{KB}isDeleted> "true"^^<http://www.w3.org/2001/XMLSchema#boolean>'
term_lit = term.replace('"', '\\"')
print(f"""SELECT DISTINCT (COUNT(DISTINCT ?mainRes) AS ?count)
WHERE {{
{{
{{
SELECT DISTINCT ?match__matchFulltext WHERE {{
?match__matchFulltext <http://jena.apache.org/text#query> ("{term_lit}" 1000000) .
}}
}}
OPTIONAL {{
?match__matchFulltext <{KB}valueCreationDate> ?valueCreationDate__matchFulltext .
FILTER NOT EXISTS {{ ?match__matchFulltext <{RDFT}> <{KB}LinkValue> . }}
FILTER NOT EXISTS {{ ?match__matchFulltext <{RDFT}> <{KB}ListValue> . }}
?containingRes__matchFulltext ?prop__matchFulltext ?match__matchFulltext .
FILTER NOT EXISTS {{ ?match__matchFulltext {DEL} . }}
}}
OPTIONAL {{
?match__matchFulltext <{RDFT}> <{KB}ListNode> .
?match__matchFulltext <{KB}hasSubListNode>* ?subNode__matchFulltext .
?listVal__matchFulltext <{KB}valueHasListNode> ?subNode__matchFulltext .
?resWithListVal__matchFulltext ?pred__matchFulltext ?listVal__matchFulltext .
FILTER NOT EXISTS {{ ?match__matchFulltext {DEL} . }}
}}
BIND(COALESCE(?containingRes__matchFulltext, ?resWithListVal__matchFulltext, ?match__matchFulltext) AS ?mainRes)
?mainRes <{KB}creationDate> ?resourceCreationDate__matchFulltext .
}}
VALUES ?mainRes__resTypes {{ {values} }}
?mainRes <{RDFT}> ?mainRes__resTypes .
FILTER NOT EXISTS {{ ?mainRes {DEL} . }}
}}
LIMIT 1""")
