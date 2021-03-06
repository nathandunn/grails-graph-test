package org.bbop.apollo

import grails.async.PromiseList
import grails.transaction.Transactional
import org.neo4j.driver.v1.Record
import org.neo4j.driver.v1.StatementResult

import static org.springframework.http.HttpStatus.*

@Transactional(readOnly = true)
class MRNAController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def showAll(Integer max) {
        params.max = Math.min(max ?: 10, 1000000000)
        String sequenceName = params.sequenceName ?: "Group2.19"
        String featureName = params.featureName ?: "*"

        long startTime = System.currentTimeMillis()

        String query = "MATCH (n:MRNA )-[o:FEATURE_LOCATION]-(q:SEQUENCE {name:'${sequenceName}'}),(n:MRNA)-[r:RELATIONSHIP]-(p) RETURN n,p,q,o LIMIT ${params.max}"
        StatementResult result = MRNA.cypherStatic(query)

        List<Map> resultList = new ArrayList<>()

        while (result.hasNext()) {
            Record record = result.next()
            resultList.add(record.asMap())
        }
        long stopTime = System.currentTimeMillis()

        respond model: [results: resultList, MRNACount: resultList.size(), time: (stopTime - startTime)]

    }

    def measureRetrievalAsync(Integer max) {

        String featureName = params.featureName ?: "Group2.19h-00001"
        Integer concurrent = (params.concurrent ?: 10) as Integer
        params.max = Math.min(max ?: 10, 1000000)

        long startTime = System.currentTimeMillis()

        int numResults = 0
        long queryTime = 0
        long totalTime = 0

        def list = new PromiseList()

        Long avgQueryTime = 0l
        Long avgRetrievalTime = 0l
        List promiseList = []

//        MRNA.async.findAllByName(featureName).onComplete() { List results ->
//            numResults += results.size()
//        }

        def results = MRNA.findAllByName(featureName)
        numResults += results.size()

        println "# of results: ${numResults}"

        long stopTime = System.currentTimeMillis()
        println "Avg retrieval time ${stopTime - startTime} for ${numResults} results and ${concurrent} concurrency."

        respond MRNA.list(params), model: [MRNACount: MRNA.count(), avgTime: stopTime - startTime, avgQueryTime: avgQueryTime, featureName: featureName], view: "index"
    }

    def measureRetrieval(Integer max) {
        List<Long> queryTimes = new ArrayList<Long>()
        List<Long> retrievalTimes = new ArrayList<Long>()
        params.max = Math.min(max ?: 10, 1000000)

        String featureName = params.featureName ?: "Group2.19h-00001"

        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis()
            String query = "MATCH (n:MRNA {name:'${featureName}'})-[:FEATURE_LOCATION]-(q:SEQUENCE),(n:MRNA {name:'${featureName}'})-[:RELATIONSHIP]-(p) RETURN n,p,q LIMIT ${params.max}"
            StatementResult result = MRNA.cypherStatic(query)
            long stopTime = System.currentTimeMillis()
            queryTimes.add(stopTime - startTime)
            startTime = System.currentTimeMillis()
            List<Record> statementResults = result.list()
            stopTime = System.currentTimeMillis()
            retrievalTimes.add(stopTime - startTime)
            println "# of results: ${statementResults.size()}"
        }
        Double avgQueryTime = queryTimes.sum() / queryTimes.size()
        println "avg query time ${avgQueryTime}"
        Double avgRetrievalTime = retrievalTimes.sum() / retrievalTimes.size()
        println "avg retrieval time ${avgRetrievalTime}"

        respond MRNA.list(params), model: [MRNACount: MRNA.count(), avgTime: avgRetrievalTime, avgQueryTime: avgQueryTime, featureName: featureName], view: "index"
    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 1000000)
//        def mrnas = params.name ?  MRNA.findAllByName("YAL023C-00001",params): MRNA.list(params)
//        println "mrnas: ${mrnas.size()}"

        def mrnas = MRNA.list(params)

        respond mrnas, model: [MRNACount: MRNA.count()]
    }

    def one() {
        println "count: " + MRNA.countByName("YAL001C-00001")
        MRNA mrna = MRNA.findByName("YAL001C-00001")
        println "properties: ${mrna.properties}"
        println "dateCreated: ${mrna.date_created}"
        respond mrna, view: 'one'
    }

    def show(MRNA mrna) {
        respond mrna
    }

    def create() {
        respond new MRNA(params)
    }

    @Transactional
    def save(MRNA mrna) {
        if (mrna == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (mrna.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond mrna.errors, view: 'create'
            return
        }

        mrna.save flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'MRNA.label', default: 'MRNA'), mrna.id])
                redirect mrna
            }
            '*' { respond mrna, [status: CREATED] }
        }
    }

    def edit(MRNA mrna) {
        respond mrna
    }

    @Transactional
    def update(MRNA mrna) {
        if (mrna == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        if (mrna.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond MRNA.errors, view: 'edit'
            return
        }

//        mrna.save flush:true
        mrna.save

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'MRNA.label', default: 'MRNA'), MRNA.id])
                redirect mrna
            }
            '*' { respond mrna, [status: OK] }
        }
    }

    @Transactional
    def delete(MRNA mrna) {

        if (mrna == null) {
            transactionStatus.setRollbackOnly()
            notFound()
            return
        }

        mrna.delete flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'mrna.label', default: 'mrna'), mrna.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'MRNA.label', default: 'MRNA'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }
}
