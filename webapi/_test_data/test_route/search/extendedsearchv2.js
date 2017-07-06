/**
 * To be run with nodejs.
 */

'use strict';

let http = require("http");

let queryArr = [];

// search for all the letters exchanged between two persons
queryArr.push(`
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
`);

// search for a letter that has the given title and mentions Isaac Newton
queryArr.push(`
      PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      
      CONSTRUCT {
          ?letter knora-api:isMainResource true .
      
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
      
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
`);

// search for a letter that has the given title and mentions Isaac Newton using a var as a value prop pred
queryArr.push(`
      PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      
      CONSTRUCT {
          ?letter knora-api:isMainResource true .
      
          ?letter a beol:letter .
      
          ?letter beol:title ?title .
      
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> .
      
      } WHERE {
          ?letter a knora-api:Resource .
          ?letter a beol:letter .
      
          ?letter ?hasTitle ?title .
          ?hasTitle knora-api:objectType xsd:string .
      
          FILTER(?hasTitle = beol:title)  
      
          ?title a xsd:string .
          FILTER(?title = "1707-05-18_2_Hermann_Jacob-Scheuchzer_Johann_Jakob")
      
          # Newton,  Isaac 1643-1727
          ?letter beol:mentionsPerson <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> .
          beol:mentionsPerson  knora-api:objectType knora-api:Resource .
      
          <http://rdfh.ch/beol/NUkE4PxyT1uEm3K9db63wQ> a knora-api:Resource .
      }
`);

// search for letters that link to another letter via standoff that is authored by a person with IAF id "120379260" and has the title "1708-03-11_Scheuchzer_Johannes-Bernoulli_Johann_I"
queryArr.push(`
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
`);

queryArr.push(`
    PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
 
    CONSTRUCT {
        ?page knora-api:isMainResource true .
        
        ?page a incunabula:page .
        
        ?page knora-api:partOf <http://data.knora.org/b6b5ff1eb703> .
    } WHERE {
    
        ?page a incunabula:page .
        ?page a knora-api:Resource .
        
        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
        knora-api:isPartOf knora-api:objectType knora-api:Resource .
        
        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
    
        ?page incunabula:seqnum ?seqnum .
        incunabula:seqnum knora-api:objectType xsd:integer .
    
        FILTER(?seqnum <= 10)
    
        ?seqnum a xsd:integer .
    
    }
`);

queryArr.push(`
    PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
 
    CONSTRUCT {
        ?page knora-api:isMainResource true .
        
        ?page a beol:page .
        
        ?page knora-api:isPartOf <http://rdfh.ch/beol/dQ1D0AjmSMCS_j4yLaSmFw> .
        
        ?page beol:seqnum ?seqnum .
        
        ?page knora-api:hasStillImageFileValue ?file .
    } WHERE {
    
        ?page a beol:page .
        ?page a knora-api:Resource .
        
        ?page knora-api:isPartOf <http://rdfh.ch/beol/dQ1D0AjmSMCS_j4yLaSmFw> .
        knora-api:isPartOf knora-api:objectType knora-api:Resource .
        
        <http://rdfh.ch/beol/dQ1D0AjmSMCS_j4yLaSmFw> a knora-api:Resource .
    
        ?page beol:seqnum ?seqnum .
        beol:seqnum knora-api:objectType xsd:integer .
    
        ?seqnum a xsd:integer .
    
        ?page knora-api:hasStillImageFileValue ?file .
        knora-api:hasStillImageFileValue knora-api:objectType knora-api:StillImageFile .
        
        ?file a knora-api:StillImageFile .
    
    }
`);

queryArr.push(`
    PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
 
    CONSTRUCT {
        ?region knora-api:isMainResource true .
        
        ?region a knora-api:Region .
        
        ?region knora-api:isRegionOf <http://data.knora.org/9d626dc76c03> .
        
        ?region knora-api:hasGeometry ?geom .
    } WHERE {
    
        ?region a knora-api:Region .
        ?region a knora-api:Resource .
        
        ?region knora-api:isRegionOf <http://data.knora.org/9d626dc76c03> .
        knora-api:isRegionOf knora-api:objectType knora-api:Resource .
        
        <http://data.knora.org/9d626dc76c03> a knora-api:Resource .
        
        ?region knora-api:hasGeometry ?geom .
        knora-api:hasGeometry knora-api:objectType knora-api:Geom .
        
        ?geom a knora-api:Geom .
        
        ?region knora-api:hasComment ?comment .
        knora-api:hasComment knora-api:objectType xsd:string .
        
        ?comment a xsd:string .
        
    }
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
                console.log(parsedData['numberOfItems'])
                console.log(rawData);
                console.log(`Duration in millis: ${duration}`);
                console.log("++++++++++")
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

