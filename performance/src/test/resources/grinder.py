# Hello World
#
# A minimal script that tests The Grinder logging facility.
#
# This script shows the recommended style for scripts, with a
# TestRunner class. The script is executed just once by each worker
# process and defines the TestRunner class. The Grinder creates an
# instance of TestRunner for each worker thread, and repeatedly calls
# the instance for each run of that thread.

from net.grinder.script.Grinder import grinder
from net.grinder.script import Test
from com.tomitribe.support import SessionServicePerf

# A shorter alias for the grinder.logger.output() method.
log = grinder.logger.output

tests = {
     "callServer1" : Test(1, "callServer1"),
     "callServer2" : Test(2, "callServer2"),
     "callLoadBalancer" : Test(3, "callLoadBalancer")
    }
loadBean = SessionServicePerf("http://localhost")
callServer1 = tests["callServer1"].wrap(loadBean)
callServer2 = tests["callServer2"].wrap(loadBean)
callLoadBalancer = tests["callLoadBalancer"].wrap(loadBean)

# A TestRunner instance is created for each thread. It can be used to
# store thread-specific data.
class TestRunner:

    # This method is called for every run.
    def __call__(self):
        callServer1.callServer1()
        callServer2.callServer2()
        callLoadBalancer.callLoadBalancer()