package dbnp.studycapturing
import org.dbnp.gdt.*
import dbnp.authentication.SecUser
import org.dbxp.sam.*

/**
 * Domain class describing the basic entity in the study capture part: the Study class.
 *
 * Revision information:
 * $Rev$
 * $Author$
 * $Date$
 */
class Study extends TemplateEntity {
	def moduleNotificationService

	SecUser owner		// The owner of the study. A new study is automatically owned by its creator.
	String title		// The title of the study
	String description	// A brief synopsis of what the study is about
	String code			// currently used as the external study ID, e.g. to reference a study in a SAM module
	Date dateCreated


	Date lastUpdated
	Date startDate
	List subjects
	List events
	List samplingEvents
	List eventGroups
	List samples
	List assays
	boolean publicstudy = false  // Determines whether anonymous users are allowed to see this study. This has only effect when published = true

    // 20120625: published default to true
    boolean published = true // Determines whether a study is private (only accessable by the owner and writers) or published (also visible to readers)

	static hasMany = [
		subjects: Subject,
		samplingEvents: SamplingEvent,
		events: Event,
		eventGroups: EventGroup,
		samples: Sample,
		assays: Assay,
		persons: StudyPerson,
		publications: Publication,
		readers: SecUser,
		writers: SecUser
	]

	static constraints = {
		title(nullable:false, blank: false, unique:true, maxSize: 255)
		owner(nullable: true, blank: true)
		code(nullable: true, blank: true, unique: true, maxSize: 255, matches: "[^ ]+")
		persons(size:1..1000)
		// TODO: add custom validator for 'published' to assess whether the study meets all quality criteria for publication
		// tested by SampleTests.testStudyPublish
	}

	// see org.dbnp.gdt.FuzzyStringMatchController and Service
	static fuzzyStringMatchable = [
	    "title",
		"code"
	]

	static mapping = {
		cache true
		autoTimestamp true
		sort "title"

		// Make sure the TEXT field description is persisted with a TEXT field in the database
		description type: 'text'
		// Workaround for bug http://jira.codehaus.org/browse/GRAILS-6754
		templateTextFields type: 'text'

	}

	/**
	 * return the domain fields for this domain class
	 * @return List
	 */
	@Override
	List<TemplateField> giveDomainFields() { return domainFields }

	static final List<TemplateField> domainFields = [
		new TemplateField(
		name: 'title',
		type: TemplateFieldType.STRING,
		required: true),
		new TemplateField(
		name: 'description',
		type: TemplateFieldType.TEXT,
		comment:'Give a brief synopsis of what your study is about',
		required: true),
		new TemplateField(
		name: 'code',
		type: TemplateFieldType.STRING,
		preferredIdentifier: true,
		comment: 'Fill out the code by which many people will recognize your study',
		required: true),
		new TemplateField(
		name: 'startDate',
		type: TemplateFieldType.DATE,
		comment: 'Fill out the official start date or date of first action',
		required: true),
//		new TemplateField(
//		name: 'published',
//		type: TemplateFieldType.BOOLEAN,
//		comment: 'Determines whether this study is published (accessible for the study readers and, if the study is public, for anonymous users). A study can only be published if it meets certain quality criteria, which will be checked upon save.',
//		required: false)
	]

	/**
	 * return the title of this study
	 */
	def String toString() {
		return ( (code) ? code : "[no code]") + " - "+ title
	}

	/**
	 * returns all events and sampling events that do not belong to a group
	 */
	def List<Event> getOrphanEvents() {
		def orphans = events.findAll { event -> !event.belongsToGroup(eventGroups) } +
		samplingEvents.findAll { event -> !event.belongsToGroup(eventGroups) }

		return orphans
	}

	/**
	 * Return the unique Subject templates that are used in this study
	 */
	def List<Template> giveSubjectTemplates() {
		TemplateEntity.giveTemplates(subjects)
	}

	/**
	 * Return all subjects for a specific template
	 * @param Template
	 * @return ArrayList
	 */
	def ArrayList<Subject> giveSubjectsForTemplate(Template template) {
		subjects.findAll { it.template.equals(template) }
	}

	/**
	 * Return all unique assay templates
	 * @return Set
	 */
	List<Template> giveAllAssayTemplates() {
		TemplateEntity.giveTemplates(((assays) ? assays : []))
	}

	/**
	 * Return all assays for a particular template
	 * @return ArrayList
	 */
	def ArrayList giveAssaysForTemplate(Template template) {
		assays.findAll { it && it.template.equals(template) }
	}

	/**
	 * Return the unique Event and SamplingEvent templates that are used in this study
	 */
	List<Template> giveAllEventTemplates() {
		// For some reason, giveAllEventTemplates() + giveAllSamplingEventTemplates()
		// gives trouble when asking .size() to the result
		// So we also use giveTemplates here
		TemplateEntity.giveTemplates(((events) ? events : []) + ((samplingEvents) ? samplingEvents : []))
	}

	/**
	 * Return all events and samplingEvenets for a specific template
	 * @param Template
	 * @return ArrayList
	 */
	def ArrayList giveEventsForTemplate(Template template) {
		def events = events.findAll { it.template.equals(template) }
		def samplingEvents = samplingEvents.findAll { it.template.equals(template) }

		return (events) ? events : samplingEvents
	}

	/**
	 * Return the unique Event templates that are used in this study
	 */
	List<Template> giveEventTemplates() {
		TemplateEntity.giveTemplates(events)
	}

	/**
	 * Return the unique SamplingEvent templates that are used in this study
	 */
	List<Template> giveSamplingEventTemplates() {
		TemplateEntity.giveTemplates(samplingEvents)
	}

	/**
	 * Returns the unique Sample templates that are used in the study
	 */
	List<Template> giveSampleTemplates() {
		TemplateEntity.giveTemplates(samples)
	}

	/**
	 * Return all samples for a specific template, sorted by subject name
	 * @param Template
	 * @return ArrayList
	 */
	def ArrayList<Subject> giveSamplesForTemplate(Template template) {
		// sort in a concatenated string as sorting on 3 seperate elements
		// in a map does not seem to work properly
		samples.findAll { it.template.equals(template) }.sort {
			"${it.parentEvent?.template}|${it.parentEvent?.startTime}|${it.parentSubject?.name}".toLowerCase()
		}
	}

	/**
	 * Returns the template of the study
	 */
	Template giveStudyTemplate() {
		return this.template
	}

	/**
	 * Delete a specific subject from this study, including all its relations
	 * @param subject The subject to be deleted
	 * @void
	 */
	void deleteSubject(Subject subject) {
		// Delete the subject from the event groups it was referenced in
		this.eventGroups.each {
			if (it.subjects?.contains(subject)) {
				it.removeFromSubjects(subject)
			}
		}

		// Delete the samples that have this subject as parent
		this.samples.findAll { it.parentSubject.equals(subject) }.each {
			this.deleteSample(it)
		}

		// This should remove the subject itself too, because of the cascading belongsTo relation
		this.removeFromSubjects(subject)

		// But apparently it needs an explicit delete() too
		subject.delete()
	}

	/**
	 * Delete an assay from the study
	 * @param Assay
	 * @void
	 */
	def deleteAssay(Assay assay) {
		if (assay && assay instanceof Assay) {

            // Delete SAMSample association
            SAMSample.executeUpdate("delete SAMSample s where s.parentAssay = :assay", [assay: assay])

			// iterate through linked samples
			assay.samples.findAll { true }.each() { sample ->
				assay.removeFromSamples(sample)
			}

			// remove this assay from the study
			this.removeFromAssays(assay)

			// and delete it explicitly
			assay.delete()
		}
	}

    /**
     * Dependencies from SAM have to be dealed with manually, so run this function before deleting a study.
     * @void
     */
    def clearSAMDependencies() {
        SAMSample.where{parentSample in (samples) ? samples : [] || parentAssay in (assays) ? assays : []}.list()*.delete()
    }

	/**
	 * Delete an event from the study, including all its relations
	 * @param Event
	 * @void
	 */
	void deleteEvent(Event event) {
		// remove event from eventGroups
		this.eventGroups.each() { eventGroup ->
			eventGroup.removeFromEvents(event)
		}

		// remove event from the study
		this.removeFromEvents(event)

		// and perform a hard delete
		event.delete()
	}

	/**
	 * Delete a sample from the study, including all its relations
	 * @param Event
	 * @void
	 */
	void deleteSample(Sample sample) {
		// remove the sample from the study
		this.removeFromSamples(sample)

        // Delete SAMSample association
        SAMSample.executeUpdate("delete SAMSample s where s.parentSample = :sample", [sample: sample])

		// remove the sample from any sampling events it belongs to
		this.samplingEvents.findAll { it.samples.any { it == sample }}.each {
			it.removeFromSamples(sample)
		}

		// remove the sample from any assays it belongs to
		this.assays.findAll { it.samples.any { it == sample }}.each {
			it.removeFromSamples(sample)
		}

		// Also here, contrary to documentation, an extra delete() is needed
		// otherwise date is not properly deleted!
		sample.delete()
	}

	/**
	 * Delete a samplingEvent from the study, including all its relations
	 * @param SamplingEvent
	 * @void
	 */
	void deleteSamplingEvent(SamplingEvent samplingEvent) {
		// remove event from eventGroups
		this.eventGroups.each() { eventGroup ->
			eventGroup.removeFromSamplingEvents(samplingEvent)
		}

		// Delete the samples that have this sampling event as parent
		this.samples.findAll { it.parentEvent.equals(samplingEvent) }.each {
			// This should remove the sample itself too, because of the cascading belongsTo relation
			this.deleteSample(it)
		}

		// Remove event from the study
		// This should remove the event group itself too, because of the cascading belongsTo relation
		this.removeFromSamplingEvents(samplingEvent)

		// But apparently it needs an explicit delete() too
		// (Which can be verified by outcommenting this line, then SampleTests.testDeleteViaParentSamplingEvent fails
		samplingEvent.delete()
	}

	/**
	 * Delete an eventGroup from the study, including all its relations
	 * @param EventGroup
	 * @void
	 */
	void deleteEventGroup(EventGroup eventGroup) {
		// If the event group contains sampling events
		if (eventGroup.samplingEvents) {
			// remove all samples that originate from this eventGroup
			if (eventGroup.samplingEvents.size()) {
				// find all samples that
				//	- are part of this study
				this.samples.findAll { sample ->
					(
						// - belong to this eventGroup
						(
							sample.parentEventGroup &&
							(
								(sample.parentEventGroup.id && eventGroup.id && sample.parentEventGroup.id == eventGroup.id)
								||
								(sample.parentEventGroup.getIdentifier() == eventGroup.getIdentifier())
								||
								sample.parentEventGroup.equals(eventGroup)
							)
						)
					)
				}
				.each() { sample ->
					// remove sample from study
					this.deleteSample(sample)
				}
			}

			// remove all samplingEvents from this eventGroup
			eventGroup.samplingEvents.findAll {}.each() {
				eventGroup.removeFromSamplingEvents(it)
			}
		}

		// If the event group contains subjects
		if (eventGroup.subjects) {
			// remove all subject from this eventGroup
			eventGroup.subjects.findAll {}.each() {
				eventGroup.removeFromSubjects(it)
			}
		}

		// remove the eventGroup from the study
		this.removeFromEventGroups(eventGroup)

		// Also here, contrary to documentation, an extra delete() is needed
		// otherwise cascaded deletes are not properly performed
		eventGroup.delete()
	}

	/**
	 * Returns true if the given user is allowed to read this study
	 */
	public boolean canRead(SecUser loggedInUser) {
		// Public studies may be read by anyone
		if( this.publicstudy ) {
			return true;
		}
		
		// Anonymous readers are only given access when published and public
		if (loggedInUser == null) {
			return false;
		}

		// Administrators are allowed to read every study
		if (loggedInUser.hasAdminRights()) {
			return true;
		}

		// Owners and writers are allowed to read this study
		if (this.owner == loggedInUser || this.writers.contains(loggedInUser)) {
			return true
		}

		// Readers are allowed to read this study when it is published
//		if (this.readers.contains(loggedInUser) && this.published) {
		if (this.readers.contains(loggedInUser)) {
			return true
		}

		return false
	}

	/**
	 * Returns true if the given user is allowed to write this study
	 */
	public boolean canWrite(SecUser loggedInUser) {
		if (loggedInUser == null) {
			return false;
		}

		// Administrators are allowed to write every study
		if (loggedInUser.hasAdminRights()) {
			return true;
		}

        return this.owner.username == loggedInUser.username || this.writers.username.contains(loggedInUser.username)
    }

	/**
	 * Returns true if the given user is the owner of this study
	 */
	public boolean isOwner(SecUser loggedInUser) {
		if (loggedInUser == null) {
			return false;
		}
		return this.owner == loggedInUser
	}

	/**
	 * Returns a list of studies that are writable for the given user
	 */
	public static giveWritableStudies(SecUser user, Integer max = null) {
		// User that are not logged in, are not allowed to write to a study
		if (user == null)
			return [];

		// Administrators are allowed to read everything
		if (user.hasAdminRights()) {
            def c = Study.createCriteria()
			return c.listDistinct {
				if (max != null) maxResults(max)
				order("title", "asc")
				
			}
		}

        def hqlString = "from Study s where s.publicstudy = true or s.owner = :user or :user in elements(s.writers) order by s.title asc"
        if (max)
            return Study.findAll(hqlString, [user: user], [max: max])
        else
            return Study.findAll(hqlString, [user: user])
	}

	/**
	 * Returns a list of studies that are readable by the given user
	 */
	public static giveReadableStudies(SecUser user, Integer max = null, int offset = 0) {

		// Administrators are allowed to read everything
		if (user == null) {
            def c = Study.createCriteria()
			return c.listDistinct {
				if (max != null) maxResults(max)
				firstResult(offset)
				order("title", "asc")
				and {
//					eq("published", true)
					eq("publicstudy", true)
				}
			}
		} else if (user.hasAdminRights()) {
            def c = Study.createCriteria()
            return c.listDistinct {
				if (max != null) maxResults(max)
				firstResult(offset)
				order("title", "asc")
			}
		} else {
            def hqlString = "from Study s where s.publicstudy = true or s.owner = :user or :user in elements(s.readers) OR :user in elements(s.writers) order by s.title asc"
            if (max)
                return Study.findAll(hqlString, [user: user], [max: max, offset: offset])
            else
                return Study.findAll(hqlString, [user: user])
		}
	}

	/**
	 * perform a text search on studies
	 * @param query
	 * @return
	 */
	public static textSearchReadableStudies(SecUser user, String query) {

		if (user == null) {
			// regular user
            def c = Study.createCriteria()
			return c.listDistinct {
				or {
					ilike("title", "%${query}%")
					ilike("description", "%${query}%")
				}
				and {
					eq("published", true)
					eq("publicstudy", true)
				}
			}
		} else if (user.hasAdminRights()) {
			// admin can search everything
            def c = Study.createCriteria()
			return c.listDistinct {
				or {
					ilike("title", "%${query}%")
					ilike("description", "%${query}%")
				}
			}
		} else {
            return Study.findAll("from Study s where s.title like '%${query}%' or s.description like '%${query}%' and (s.publicstudy = true or s.owner = :user or :user in elements(s.readers) OR :user in elements(s.writers)) order by s.title asc", [user: user])
		}
	}

	/**
	 * Returns the number of public studies
	 * @return int
	 */
	public static countPublicStudies() { return countPublicStudies(true) }
	public static countPublicStudies(boolean published) {
		def c = Study.createCriteria()
		return (c.listDistinct {
			and {
				eq("published", published)
				eq("publicstudy", true)
			}
		}).size()
	}

	/**
	 * Returns the number of private studies
	 * @return int
	 */
	public static countPrivateStudies() { return countPrivateStudies(false) }
	public static countPrivateStudies(boolean published) {
		def c = Study.createCriteria()
		return (c.listDistinct {
			and {
				eq("publicstudy", false)
			}
			or {
				eq("published", published)
				eq("publicstudy", true)
			}
		}).size()
	}

	/**
	 * Returns the number of studies that are readable by the given user
	 */
	public static countReadableStudies(SecUser user) {
		def c = Study.createCriteria()

		// got a user?
		if (user == null) {
			return c.count {
				and {
					eq("published", true)
					eq("publicstudy", true)
				}
			}
		} else if (user.hasAdminRights()) {
			// Administrators are allowed to read everything
			return Study.count()
		} else {
            return Study.executeQuery("select count(*) from Study s where s.publicstudy = true or s.owner = :user or :user in elements(s.readers) OR :user in elements(s.writers)", [user: user])[0]
		}
	}

	/**
	 * Returns the number of studies that are readable & writable by the given user
	 */
	public static countReadableAndWritableStudies(SecUser user) {
		def c = Study.createCriteria()

		// got a user?
		if (user == null) {
			return 0
		} else if (user.hasAdminRights()) {
			return Study.count()
		} else {
            return Study.executeQuery("select count(*) from Study s where s.publicstudy = true or s.owner = :user or :user in elements(s.readers) OR :user in elements(s.writers)", [user: user])[0]
		}
	}

	/**
	 * Basic equals method to check whether objects are equals, by comparing the ids
	 * @param o		Object to compare with
	 * @return		True iff the id of the given Study is equal to the id of this Study
	 */
	public boolean equals( Object o ) {
		if( o == null )
			return false;

		if( !( o instanceof Study ) )
			return false

		Study s = (Study) o;

		return this.id == s.id
	}

    /**
     * Returns the minimum and maximum date of the events of this study
     * @return  A map containing absolute minDate and maxDate (not relative)
     */
    def getMinMaxEventDate() {
        long minDate = Long.MAX_VALUE;
        long maxDate = Long.MIN_VALUE;
        this.events.each {
            if(it.startTime < minDate) {
                minDate = it.startTime;
            }
            if(it.endTime > maxDate) {
                maxDate = it.endTime;
            }
            if(it.startTime > maxDate) {
                maxDate = it.startTime;
            }
        }
        this.samplingEvents.each {
            if(it.startTime < minDate) {
                minDate = it.startTime;
            }
            if(it.startTime > maxDate) {
                maxDate = it.startTime;
            }
        }
        long lngStartDate  = (Long) this.startDate.getTime();
        return ["minDate" : new Date( lngStartDate + minDate * 1000 ), "maxDate" : new Date( lngStartDate + maxDate * 1000 )];
    }

    // This closure is used in the before{Insert,Update,Delete} closures below.
    // It is necessary to prevent flushing in the same session as a top level
    // database action such as 'save' or 'addTo...'. This confuses hibernate and
    // produces hard to trace errors.
    // The same holds for flushing during validation (but that's not the case
    // here).
    // http://grails.1312388.n4.nabble.com/Grails-hibernate-flush-causes-IndexOutOfBoundsException-td3031979.html
    static manualFlush(closure) {
        withSession {session ->
            def save
            try {
                save = session.flushMode
                session.flushMode = org.hibernate.FlushMode.MANUAL
                closure()
            } finally {
                if (save) {
                    session.flushMode = save
                }
         }
        }
    }

	// Send messages to modules about changes in this study
	def beforeInsert = {
        manualFlush{
            moduleNotificationService.invalidateStudy( this )
        }
	}
	def beforeUpdate = {
        manualFlush{
            moduleNotificationService.invalidateStudy( this )
        }
	}
	def beforeDelete = {
		manualFlush{
            moduleNotificationService.invalidateStudy( this )
        }
	}

    /**
     * return the unique species
     * @see dbnp.query.StudyCompareController
     */
    def uniqueSpecies = {
        return subjects.collect{ it.species }.unique()
    }

    /**
     * return the unique event templates
     * @see dbnp.query.StudyCompareController
     */
    def uniqueEventTemplates = {
        return events.collect{ it.template }.unique()
    }

    /**
     * return the unique sampling event templates
     * @see dbnp.query.StudyCompareController
     */
    def uniqueSamplingEventTemplates = {
        return samplingEvents.collect{ it.template }.unique()
    }

    /**
     * return the unique assay modules
     * @see dbnp.query.StudyCompareController
     */
    def uniqueAssayModules = {
        return assays.collect{ it.module }.unique()
    }

    public String viewUrl() {
		throw new UnsupportedOperationException("Not implemented");
	}
}
