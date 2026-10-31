
Reliable service slice:

bounded course registration

task decomposition ?

AI review and independent verification


problem statement:
Enrollment (customer should be able to enroll a course)
drop (customer can drop a course?)

FIFO waitlist
Dedup(learner should be able to enroll only once)

When the course slots fulled, eligible learners should be in waitlist(status)
If the seat opens, earliest eligible learner should promote.

Components:
Simple Entity design choices,
Simple in memory store,
accept input using a cmd

Prerequisite and waitlist Assumptions:
Assuming user/candidate should provide a valid:
    Email Address, Phone number, Identity proof.
    Also possible to restrict to eligible Education qualification: say candidate should have a certain degree obtained etc.

An ineligible learner can skip until have above requirements met.

Acceptance criteria:
design details:
Expose a POST registration API:
  it should perform below things in order: 
    First verify/validate the user information.
        If the user qualifies:
            Then push the request to a stream(kafka for reliability, depends on capacity scale etc):
            for simplicity use a FIFO queue.
        If No:
            Send the response as not qualified stating the policy or details that not meet the policy etc.

Additional req: Even if the course is full, we can design that a threshold/limit is applied to the waitlist

Core registration/enrollment service:
    Handle deduplication:
        validate whether the user(userId AND Identity number) already registered to the course(courseId)
        if above succeeds:
            Proceed with registration and send a notification(email):
            For simplcity, I'll just display the status in CMD.

Expose a POST course Drop/withdraw API:
    First, validate is the drop request valid or not - by checking the enrolled course details.
    Check for dedup, if candidate already withdrawn: respond with Aleady done message(something like this)
If yes:
        update the status of the enrollment to something like drop/withdrawn.
        Then, 
            we're suppose to perform additional steps:
                We need to update the course availability count,
                And make an eligibile(FIFO) user/learner to enroll and send the notification etc.

Important point:
    Invalid requests and duplicate drops
    

Tests/Regression:
Need to implement tests covering all scenarios: availabile course capacity,
full course capacity, duplicate requests, FIFO promotion(?),
prequisite failure, and invalid or repeated drop requests.

Entity/schema design:
Let's keep it simple.

Entities required:

User Table:
    userId(Primary key): long
    username: string/varchar(40)
    userIdentityNumber: string  -  stick to only one type of identity
    email Address: string/varchar(40)
    phone number: String/varchar(10/13)
    additionalInfo: AdditionalInformation

For simplicty, I choose to put the additional details like education etc in one simple entity.
AdditionalInformation:
    Highest Qualification: Enum (a better choice?)
    -- will add more like CGPA and validate that with minimum etc.(Additional build/day 2 requirment)
        
Course Table:
    courseid: long
    courseName: stirng
    price: double
    -- etc

Entity relationships:
 A user can enroll one or multiple courses.
 A course can be enrolled by one or multiple users.
So, cardinality ratio comes to be m:m
Create a seperate table. here the table should not only maintain the course and user Ids but also addiitonal attributes,

UsersCourses Table:
    id: long
    userId: long
    courseId: long
    enrolledStatus: Enum
    withdrawnStatus: Enum  -- caveat, I'm thinking we can separate the status as cartesian product for better readility. I inspired this from building FSMs
    -- etc

