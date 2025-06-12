buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java21'

  doDocker = {
    buildJavaDocker {
      dockerDir = 'okapi-core'
      overrideConfig  = false
      publishMaster = true
      healthChk = true
      healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:9130/_/proxy/health || exit 1'
      runArgs = 'dev'
    }
  }
}
