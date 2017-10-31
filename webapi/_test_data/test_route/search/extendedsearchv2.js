#!/usr/bin/env node
/**
 * To be run with nodejs.
 */

'use strict';

let http = require("http");

let queryArr = [];

// search for all the letters exchanged between two persons
queryArr.push(`
    PREFIX beol: <http://0.0.0.0:3333/ontology/beol/simple/v2#>
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
    
    CONSTRUCT {
        ?letter knora-api:isMainResource true .
    
        ?letter ?linkingProp1  <http://rdfh.ch/beol/ZkJcQg9yTmyMY_J6nnubxA> .

        ?letter ?linkingProp2  <http://rdfh.ch/beol/_yblAQMwT2un_xN7UaVWrg> .
        
        <http://rdfh.ch/beol/ZkJcQg9yTmyMY_J6nnubxA> beol:hasFamilyName ?name .
        

    } WHERE {
        ?letter a knora-api:Resource .
        ?letter a beol:letter .
        
        ?letter beol:creationDate ?date .
        beol:creationDate knora-api:objectType knora-api:Date .
        
        ?date a knora-api:Date .
    
        # Scheuchzer, Johann Jacob 1672-1733
        ?letter ?linkingProp1  <http://rdfh.ch/beol/ZkJcQg9yTmyMY_J6nnubxA> .
        ?linkingProp1 knora-api:objectType knora-api:Resource .
        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
        
        <http://rdfh.ch/beol/ZkJcQg9yTmyMY_J6nnubxA> a knora-api:Resource .

        <http://rdfh.ch/beol/ZkJcQg9yTmyMY_J6nnubxA> beol:hasFamilyName ?name .
        
        beol:hasFamilyName knora-api:objectType xsd:string .
        ?name a xsd:string .


        # Hermann, Jacob 1678-1733
        ?letter ?linkingProp2 <http://rdfh.ch/beol/_yblAQMwT2un_xN7UaVWrg> .
        ?linkingProp2 knora-api:objectType knora-api:Resource .
    
        FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
    
        <http://rdfh.ch/beol/_yblAQMwT2un_xN7UaVWrg> a knora-api:Resource .
    } ORDER BY ?date
`);

// search for a letter that has the given title and mentions Isaac Newton
queryArr.push(`
      PREFIX beol: <http://0.0.0.0:3333/ontology/beol/simple/v2#>
      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      
      CONSTRUCT {
          ?letter knora-api:isMainResource true .
      
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
      
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> .
      
      } WHERE {
          ?letter a knora-api:Resource .
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
          beol:title knora-api:objectType xsd:string .
      
          ?title a xsd:string .
          FILTER(?title = "1707-05-18_2_Hermann_Jacob-Scheuchzer_Johann_Jakob")
      
          # Newton,  Isaac 1643-1727
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> .
          beol:mentionsPerson  knora-api:objectType knora-api:Resource .
      
          <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> a knora-api:Resource .
      } ORDER BY ?title
`);

// search for a letter that has the given title and mentions Isaac Newton using a var as a value prop pred
queryArr.push(`
      PREFIX beol: <http://0.0.0.0:3333/ontology/beol/simple/v2#>
      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      
      CONSTRUCT {
          ?letter knora-api:isMainResource true .
      
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
      
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> .
      
      } WHERE {
          ?letter a knora-api:Resource .
          ?letter a beol:letter .
      
          ?letter ?hasTitle ?title .
          ?hasTitle knora-api:objectType xsd:string .
      
          FILTER(?hasTitle = beol:title)  
      
          ?title a xsd:string .
          FILTER(?title = "1707-05-18_2_Hermann_Jacob-Scheuchzer_Johann_Jakob")
      
          # Newton,  Isaac 1643-1727
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> .
          beol:mentionsPerson  knora-api:objectType knora-api:Resource .
      
          <http://rdfh.ch/beol/BPW4_gnxQBC4iWm3F6ni8w> a knora-api:Resource .
      }
`);

// search for a letter with the given title that links to another letter via standoff that is authored by a person with IAF id "120379260" and has the title "1708-03-11_Scheuchzer_Johannes-Bernoulli_Johann_I"
queryArr.push(`
PREFIX beol: <http://0.0.0.0:3333/ontology/beol/simple/v2#>
PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

CONSTRUCT {
    ?letter knora-api:isMainResource true .

    ?letter a beol:letter .

    ?letter beol:title ?title .

    ?letter knora-api:hasStandoffLinkTo ?anotherLetter .

    ?anotherLetter beol:hasAuthor ?author .

    ?author beol:hasIAFIdentifier ?gnd .
} WHERE {

    ?letter a beol:letter .
    ?letter a knora-api:Resource .

    ?letter knora-api:hasStandoffLinkTo ?anotherLetter .
    knora-api:hasStandoffLinkTo knora-api:objectType knora-api:Resource .
    ?anotherLetter a knora-api:Resource .

    ?letter beol:title ?title .
    FILTER(?title = "1708-03-11_Scheuchzer_Johannes-Bernoulli_Johann_I")
    
    ?title a xsd:string .
    beol:title knora-api:objectType xsd:string .

    ?anotherLetter beol:hasAuthor ?author .
    beol:hasAuthor knora-api:objectType knora-api:Resource .

    # Scheuchzer, Johann 1684-1738
    ?author a beol:person .
    ?author a knora-api:Resource .

    ?author beol:hasIAFIdentifier ?gnd .
    FILTER(?gnd = "120379260")
    
    ?gnd a xsd:string .
    beol:hasIAFIdentifier knora-api:objectType xsd:string .
}
`);

// query all link objects that refer to an incunabula:book
// Attention: link objects have several instances of knora-api:hasLinkTo
queryArr.push(`
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
    
    CONSTRUCT {
        ?linkObj knora-api:isMainResource true .
        
        ?linkObj knora-api:hasLinkTo ?book .
        
    } WHERE {
        ?linkObj a knora-api:Resource .
        ?linkObj a knora-api:LinkObj .
        
        ?linkObj knora-api:hasLinkTo ?book .
        knora-api:hasLinkTo knora-api:objectType knora-api:Resource .
        
        ?book a knora-api:Resource .
        ?book a incunabula:book . 
     
        ?book incunabula:title ?title .
        
        incunabula:title knora-api:objectType xsd:string .

        ?title a xsd:string .
        
    }

`);


// query all link objects that refer to an incunabula:book
// Attention: link objects have several instances of knora-api:hasLinkTo
queryArr.push(`
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
    
    CONSTRUCT {
        ?linkObj knora-api:isMainResource true .
        
    } WHERE {
        ?linkObj a knora-api:Resource .
        ?linkObj a incunabula:book .
        
    }

`);

queryArr.push(`
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
    PREFIX beol: <http://0.0.0.0:3333/ontology/beol/simple/v2#>
    
    CONSTRUCT {
        ?letter knora-api:isMainResource true .
        
        #?letter beol:hasText ?text .
        
    } WHERE {
        ?letter a knora-api:Resource .
        ?letter a beol:letter .
        
        ?letter beol:hasText ?text .
        
        beol:hasText knora-api:objectType xsd:string .

        ?text a xsd:string .
        
        
    } OFFSET 0

`);



function runQuery(queryStrArr, index) {

    if (index >= queryStrArr.length) return;

    let options = {
        host: 'localhost',
        port: 3333,
        path: '/v2/searchextended/' + encodeURIComponent(queryStrArr[index])
    };

    let timeStart = new Date();

    return http.get(options, (res) => {
        const { statusCode } = res;
        const contentType = res.headers['content-type'];

        let error;
        if (statusCode !== 200) {
            error = new Error('Request Failed.\n' +
            `Status Code: ${statusCode}`);
        } else if (!/^application\/json/.test(contentType)) {
            error = new Error('Invalid content-type.\n' +
                `Expected application/json but received ${contentType}`);
        }

        if (error) {
            console.error(error.message);

            let errMsg = '';
            res.on('data', (chunk) => {
                errMsg += chunk;
            });

            res.on('end', () => {
                console.log(errMsg);
            });
            // consume response data to free up memory
            res.resume();
            return;
        }

        res.setEncoding('utf8');
        let rawData = '';

        res.on('data', (chunk) => {
            rawData += chunk;
        });

        res.on('end', () => {
            try {
                let timeEnd = new Date();
                let duration = timeEnd - timeStart;
                const parsedData = JSON.parse(rawData);
                console.log(parsedData['schema:numberOfItems']);
                console.log(rawData);
                console.log(`Duration in millis: ${duration}`);
                console.log("++++++++++");
                runQuery(queryStrArr, index+1);
            } catch (e) {
                console.error(e.message);
            }
        });

        res.on('error', (e) => {
            console.error(`Got error: ${e.message}`);
        });

    });
}

runQuery(queryArr, 0);

