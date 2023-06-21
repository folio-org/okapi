@Library ('folio_jenkins_shared_libs') _

buildMvn {
  publishModDescriptor = 'no'
  mvnDeploy = 'yes'
  buildNode = 'jenkins-agent-java17'
  buildDeb = true

  doDocker = {
    buildJavaDocker {
      dockerDir = 'okapi-core'
      overrideConfig  = 'no'
      publishMaster = 'yes'
      healthChk = 'yes'
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:9130/_/proxy/health || exit 1'
      runArgs = 'dev'
    }
  }
}

