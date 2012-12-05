log4j = {
    // Example of changing the log pattern for the default console
    // appender:
    appenders {
        console name: 'stdout', layout: pattern(conversionPattern: '%d [%t] %-5p %c - %m%n')
        file name:'logfile', file:'merganser.log'
    }

    root {
        info 'logfile'
    }

    error  'org.codehaus.griffon'

    info   'griffon.util',
           'merganser',
//           'griffon.core',
//           'griffon.swing',
           'griffon.app'
}

