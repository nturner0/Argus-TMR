package chipyard

import org.chipsalliance.cde.config.{Config}

class TMRConfig extends Config(
  new tmr.WithNTMRCores(1) ++
  new chipyard.config.AbstractConfig)
