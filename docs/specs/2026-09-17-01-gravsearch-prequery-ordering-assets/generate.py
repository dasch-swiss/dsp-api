#!/usr/bin/env python3
"""Generates the Phase 3 spike layout files S<n>-<letter>.rq (DEV-7287).

Every layout of a case has the identical pattern set, SELECT clause, FILTERs,
FILTER NOT EXISTS guards and solution modifiers; layouts differ only in the order
of the WHERE clause's leading patterns. That is the whole point of the spike, so
the file bodies are generated rather than hand-written.

One documented exception: **S5-C is not a permutation of S5-A/S5-B.** A and B carry the literal as a
statement object (`?v valueHasString "<lit>"`); C carries the variable form plus an equality FILTER,
which is what the pipeline emits today. C is a baseline only, and S5's D13 decision is taken on
A versus B alone.

Run from this directory: python3 generate.py
"""

import pathlib

HERE = pathlib.Path(__file__).parent

KB = "http://www.knora.org/ontology/knora-base#"
TANNER = "http://www.knora.org/ontology/0102/scenario-tanner#"
EKWS = "http://www.knora.org/ontology/0812/ekws#"
RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
RDF_OBJECT = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object"
LABEL = "http://www.w3.org/2000/01/rdf-schema#label"
XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean"

PROJECT_0102 = "http://rdfh.ch/projects/0102"
PROJECT_0812 = "http://rdfh.ch/projects/Wacpqk4-SfujXYw5EeUoCw"
CONCEPT_IRI = "http://rdfh.ch/0812/HF06nlmuSyyOIkqLCgeF7Q"  # median in-degree (8)
LIST_NODE = "http://rdfh.ch/lists/0812/JS06AyiJR-qRce2laMtF1w"
SHELF_LITERAL = "CH_CS_CSL-020-01-08-01-01"  # exactly one hit

TANNER_DOCUMENT_CLASSES = sorted(
    TANNER + c
    for c in (
        "Document",
        "Script",
        "Production",
        "Correspondence",
        "CriticalReception",
        "Promotion",
        "InterviewArchive",
    )
)
RESOURCE_CLOSURE = sorted(
    line.strip()
    for line in (HERE / "resource-closure.txt").read_text().splitlines()
    if line.strip()
)


def values(var, iris):
    return "VALUES ?%s { %s }" % (var, " ".join("<%s>" % i for i in iris))


def fne(term):
    return 'FILTER NOT EXISTS {\n %s <%sisDeleted> "true"^^<%s> .\n\n}' % (
        term,
        KB,
        XSD_BOOLEAN,
    )


def query(select, patterns, tail, main="?mainRes"):
    body = "\n".join(patterns)
    return "SELECT DISTINCT %s\nWHERE {\n%s\n}\nGROUP BY %s\nORDER BY ASC(%s)\nLIMIT 25\n" % (
        select,
        "\n".join([body] + tail),
        main,
        main,
    )


def concat(var):
    return (
        "(GROUP_CONCAT(DISTINCT(IF(BOUND(?%s), STR(?%s), \"\")); SEPARATOR='') AS ?%s__Concat)"
        % (var, var, var)
    )


files = {}

# --- S1: tanner + project 0102; only the project statement's position varies ---
s1_values = values("mainRes__resTypes", TANNER_DOCUMENT_CLASSES)
s1_type = "?mainRes <%s> ?mainRes__resTypes ." % RDF_TYPE
s1_project = "?mainRes <%sattachedToProject> <%s> ." % (KB, PROJECT_0102)
s1_label = "?mainRes <%s> ?l ." % LABEL
s1_shelf = "?mainRes <%shasShelfNumber> ?v ." % TANNER
s1_vhs = "?v <%svalueHasString> ?s ." % KB
s1_tail = [
    'FILTER((?s = "%s"^^<%s>))' % (SHELF_LITERAL, XSD_STRING),
    fne("?mainRes"),
    fne("?v"),
]
s1_select = "?mainRes " + concat("v")
S1 = {
    "A": [s1_values, s1_type, s1_project, s1_label, s1_shelf, s1_vhs],
    "B": [s1_project, s1_values, s1_type, s1_label, s1_shelf, s1_vhs],
    "C": [s1_project, s1_label, s1_shelf, s1_vhs, s1_values, s1_type],
    "D": [s1_values, s1_type, s1_label, s1_shelf, s1_vhs, s1_project],
}
for k, v in S1.items():
    files["S1-%s.rq" % k] = query(s1_select, v, s1_tail)

# --- S2: classless (full Resource closure VALUES) + project 0102 ---
s2_values = values("mainRes__resTypes", RESOURCE_CLOSURE)
s2_type = "?mainRes <%s> ?mainRes__resTypes ." % RDF_TYPE
s2_project = "?mainRes <%sattachedToProject> <%s> ." % (KB, PROJECT_0102)
s2_label = "?mainRes <%s> ?l ." % LABEL
# The plan's literal "^Brief" matches nothing on stage: project 0102 is a French corpus,
# and the only 0102 labels starting with "Types" belong to knora-base:ListNodes, which are
# not in the Resource closure. "^Le" matches 93 resources. The two zero-row variants were
# measured first and are kept in results.csv as S2norows ("^Brief") and S2zero ("^Types").
s2_tail = ['FILTER(regex(?l, "^Le", "i"))', fne("?mainRes")]
S2 = {
    "A": [s2_project, s2_label, s2_values, s2_type],
    "B": [s2_values, s2_type, s2_project, s2_label],
    "C": [s2_project, s2_values, s2_type, s2_label],
}
for k, v in S2.items():
    files["S2-%s.rq" % k] = query("?mainRes", v, s2_tail)

# --- S3: link target 0812 (bound-IRI anchors) ---
s3_type_obj = "?mainRes <%s> <%sObject> ." % (RDF_TYPE, EKWS)
s3_concept = "?mainRes <%shasConcept> <%s> ." % (EKWS, CONCEPT_IRI)
s3_cvalue = "?mainRes <%shasConceptValue> ?lv ." % EKWS
s3_type_lv = "?lv <%s> <%sLinkValue> ." % (RDF_TYPE, KB)
s3_object = "?lv <%s> <%s> ." % (RDF_OBJECT, CONCEPT_IRI)
s3_tail = [fne("?mainRes"), fne("?lv"), fne("<%s>" % CONCEPT_IRI)]
S3 = {
    "A": [s3_concept, s3_cvalue, s3_object, s3_type_lv, s3_type_obj],
    "B": [s3_object, s3_cvalue, s3_concept, s3_type_lv, s3_type_obj],
    "C": [s3_type_obj, s3_concept, s3_cvalue, s3_type_lv, s3_object],
}
for k, v in S3.items():
    files["S3-%s.rq" % k] = query("?mainRes", v, s3_tail)

# --- S4: list node 0812 (the DEV-7287 stage matrix, re-measured with this harness) ---
s4_type = "?mainRes <%s> <%sObject> ." % (RDF_TYPE, EKWS)
s4_medium = "?mainRes <%shasMedium> ?li ." % EKWS
s4_vln = "?li <%svalueHasListNode> ?lnv ." % KB
s4_path = "<%s> <%shasSubListNode>* ?lnv ." % (LIST_NODE, KB)
s4_tail = [fne("?mainRes"), fne("?li")]
s4_select = "?mainRes " + concat("li")
S4 = {
    "A": [s4_type, s4_medium, s4_vln, s4_path],
    "B": [s4_path, s4_vln, s4_medium, s4_type],
    "C": [s4_path, s4_type, s4_medium, s4_vln],
    "D": [s4_path, s4_vln, s4_type, s4_medium],
}
for k, v in S4.items():
    files["S4-%s.rq" % k] = query(s4_select, v, s4_tail)

# --- S5: bound literal as anchor (S1 without the project statement) ---
s5_literal = '?v <%svalueHasString> "%s"^^<%s> .' % (KB, SHELF_LITERAL, XSD_STRING)
s5_tail_ab = [fne("?mainRes"), fne("?v")]
s5_tail_c = [
    'FILTER((?s = "%s"^^<%s>))' % (SHELF_LITERAL, XSD_STRING),
    fne("?mainRes"),
    fne("?v"),
]
files["S5-A.rq"] = query(
    s1_select, [s5_literal, s1_shelf, s1_values, s1_type, s1_label], s5_tail_ab
)
files["S5-B.rq"] = query(
    s1_select, [s1_values, s1_type, s1_shelf, s5_literal, s1_label], s5_tail_ab
)
files["S5-C.rq"] = query(
    s1_select, [s1_values, s1_type, s1_label, s1_shelf, s1_vhs], s5_tail_c
)

# --- S6: T2 anchor vs project (S3 layout A plus attachedToProject <0812>) ---
s6_project = "?mainRes <%sattachedToProject> <%s> ." % (KB, PROJECT_0812)
S6 = {
    "A": [s3_concept, s3_cvalue, s3_object, s6_project, s3_type_lv, s3_type_obj],
    "B": [s6_project, s3_concept, s3_cvalue, s3_object, s3_type_lv, s3_type_obj],
}
for k, v in S6.items():
    files["S6-%s.rq" % k] = query("?mainRes", v, s3_tail)

# --- S7: T6 tie-break, project fixed in second position ---
S7 = {
    "A": [s1_values, s1_type, s1_project, s1_label, s1_shelf, s1_vhs],
    "B": [s1_values, s1_type, s1_project, s1_shelf, s1_vhs, s1_label],
}
for k, v in S7.items():
    files["S7-%s.rq" % k] = query(s1_select, v, s1_tail)

# --- S8: anchorless link query 0812 (hasCreator/Person has 0 hits on stage; substituted) ---
s8_type_obj = "?mainRes <%s> <%sObject> ." % (RDF_TYPE, EKWS)
s8_link = "?mainRes <%shasConcept> ?p ." % EKWS
s8_lvalue = "?mainRes <%shasConceptValue> ?lv ." % EKWS
s8_type_lv = "?lv <%s> <%sLinkValue> ." % (RDF_TYPE, KB)
s8_object = "?lv <%s> ?p ." % RDF_OBJECT
s8_type_p = "?p <%s> <%sConcept> ." % (RDF_TYPE, EKWS)
s8_tail = [fne("?mainRes"), fne("?lv"), fne("?p")]
s8_select = "?mainRes " + concat("p")
S8 = {
    "A": [s8_type_obj, s8_link, s8_lvalue, s8_object, s8_type_p, s8_type_lv],
    "B": [s8_link, s8_lvalue, s8_object, s8_type_obj, s8_type_p, s8_type_lv],
    "C": [s8_type_lv, s8_object, s8_lvalue, s8_link, s8_type_obj, s8_type_p],
}
for k, v in S8.items():
    files["S8-%s.rq" % k] = query(s8_select, v, s8_tail)

# --- Follow-ups added after review checkpoint 3 ---

# S8w: S8 re-measured with layout C warm and inside the round-robin. The protocol's run-once
# exemption had recorded C's *warm-up* run, i.e. the coldest query of the case, against warm
# medians for A and B. S8w-A/B/C are byte-identical to S8-A/B/C.
for k in ("A", "B", "C"):
    files["S8w-%s.rq" % k] = files["S8-%s.rq" % k]

# S8w-D isolates the two factors S8-C changes at once. S8-C leads with the unselective-technical
# `?lv a knora-base:LinkValue` statement AND traverses the link chain backwards. D leads with the
# same type statement but keeps A's forward chain, so D-vs-A measures "LinkValue leads" alone.
files["S8w-D.rq"] = query(
    s8_select,
    [s8_type_lv, s8_link, s8_lvalue, s8_object, s8_type_obj, s8_type_p],
    s8_tail,
)

# S2big: S2 against project 0812 instead of the small 0102. S2's conclusion (the technical
# closure VALUES must outrank attachedToProject) is about a ratio between project size and store
# size, which is a data property, not an engine property, so it needs a second project. 0812 has
# 111939 ekws:Object alone, against 0102's 17949 Pages.
s2big_project = "?mainRes <%sattachedToProject> <%s> ." % (KB, PROJECT_0812)
s2big_tail = ['FILTER(regex(?l, "^Form", "i"))', fne("?mainRes")]
S2BIG = {
    "A": [s2big_project, s2_label, s2_values, s2_type],
    "B": [s2_values, s2_type, s2big_project, s2_label],
    "C": [s2big_project, s2_values, s2_type, s2_label],
}
for k, v in S2BIG.items():
    files["S2big-%s.rq" % k] = query("?mainRes", v, s2big_tail)

for name, text in sorted(files.items()):
    (HERE / name).write_text(text)
print("wrote %d layout files" % len(files))
