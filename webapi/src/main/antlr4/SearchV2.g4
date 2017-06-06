grammar SearchV2;

pattern: ( triple | union )+ ;

triple: variable property expr '.' ;

union: '{' triple+ '}' 'UNION' '{' triple+ '}' ('UNION' '{' triple+ '}')* ;

expr: literal
    | function
    | variable
    ;

literal: STRING
       | BOOLEAN
       | int
       | decimal
       | date
       | iri
       ;

int: 'int(' + INT + ')' ;

decimal: 'decimal(' + DECIMAL + ')' ;

date: 'date(' + DATE + ')' ;

iri: 'iri(' + STRING + ')' ;

function: ('gt' | 'lt' | 'ge' | 'le' | 'contains' | 'notContains') '(' expr ')' ;

variable: NAME ;

property: NAME ':' NAME ;

NAME: [a-zA-Z]+ [a-zA-Z0-9_]* ;

WS: [ \t\r\n]+ -> skip ;

STRING: '"' (ESC|.)*? '"' ;

fragment
ESC : '\\"' | '\\\\' ; // 2-char sequences \" and \\

INT: 'int(' DIGIT+ ')' ;

DECIMAL: 'decimal(' DIGIT+ '.' DIGIT* ')' ;

fragment
DIGIT : [0-9] ; // match single digit

BOOLEAN: 'true' | 'false' ;

DATE: '"' ('GREGORIAN' | 'JULIAN') ':'  YEAR ('-' MONTH ('-' DAY)?)? (':' YEAR ('-' MONTH ('-' DAY)?)?)? '"' ;

YEAR: DIGIT DIGIT DIGIT DIGIT ;

MONTH: DIGIT DIGIT? ;

DAY: DIGIT DIGIT? ;
