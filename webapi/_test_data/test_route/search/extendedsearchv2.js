/**
 * To be run with nodejs.
 */

'use strict';

let http = require("http");

// search for all the letters exchanged between two persons
let query = `
    PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
    
    CONSTRUCT {
        ?letter knora-api:isMainResource true .
    
        ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .

        ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .

} WHERE {
        ?letter a knora-api:Resource .
        ?letter a beol:letter .
    
        # Scheuchzer, Johann Jacob 1672-1733
        ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
        ?linkingProp1 knora-api:objectType knora-api:Resource .
        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
    
        <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .

        # Hermann, Jacob 1678-1733
        ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
        ?linkingProp2 knora-api:objectType knora-api:Resource .
    
        FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
    
        <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
}
`;

// search for a letter that has the given title and mentions Isaac Newton
let query2 = `
      PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      
      CONSTRUCT {
          ?letter knora-api:isMainResource true .
      
          ?letter a beol:letter .
      
          ?letter beol:title "1707-05-18_2_Hermann_Jacob-Scheuchzer_Johann_Jakob" .
      
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> .
      
      } WHERE {
          ?letter a knora-api:Resource .
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
          beol:title knora-api:objectType xsd:string .
      
          ?title a xsd:string .
          FILTER(?title = "1707-05-18_2_Hermann_Jacob-Scheuchzer_Johann_Jakob")
      
          # Newton,  Isaac 1643-1727
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> .
          beol:mentionsPerson  knora-api:objectType knora-api:Resource .
      
          <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> a knora-api:Resource .
      }
`;

// search for letters that link to another letter via standoff that is authored by a person with IAF id "120379260" and has the title "1708-03-11_Scheuchzer_Johannes-Bernoulli_Johann_I"
let query3 = `
PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>

CONSTRUCT {
    ?letter knora-api:isMainResource true .

    ?letter a beol:letter .

    ?letter knora-api:hasStandoffLinkTo ?anotherLetter .

    ?anotherLetter beol:hasAuthor ?author .

    ?author beol:hasIAFIdentifier "120379260" .
} WHERE {

    ?letter a beol:letter .
    ?letter a knora-api:Resource .

    ?letter knora-api:hasStandoffLinkTo ?anotherLetter .
    knora-api:hasStandoffLinkTo knora-api:objectType knora-api:Resource .
    ?anotherLetter a knora-api:Resource .

    ?letter beol:title "1708-03-11_Scheuchzer_Johannes-Bernoulli_Johann_I" .
    beol:title knora-api:objectType xsd:string .

    ?anotherLetter beol:hasAuthor ?author .
    beol:hasAuthor knora-api:objectType knora-api:Resource .

    # Scheuchzer, Johann 1684-1738
    ?author a beol:person .
    ?author a knora-api:Resource .

    ?author beol:hasIAFIdentifier "120379260" .
    beol:hasIAFIdentifier knora-api:objectType xsd:string .
}
`;


let options = {
    host: 'localhost',
    port: 3333,
    path: '/v2/searchextended/' + encodeURIComponent(query2)
};

let timeStart = new Date();

http.get(options, (res) => {
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
            //const parsedData = JSON.parse(rawData);
            console.log(rawData);
            console.log(`Duration in millis: ${duration}`);
        } catch (e) {
            console.error(e.message);
        }
    });

    res.on('error', (e) => {
        console.error(`Got error: ${e.message}`);
    });

});

