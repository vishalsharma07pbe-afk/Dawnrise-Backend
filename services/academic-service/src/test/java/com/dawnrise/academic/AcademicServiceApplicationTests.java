package com.dawnrise.academic;

import com.dawnrise.academic.academicyearrollover.repository.AcademicYearStructureRolloverOperationRepository;
import com.dawnrise.academic.common.integration.school.SchoolTimeZoneClient;
import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.academiccalendar.repository.AcademicCalendarDayRepository;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.repository.StudentEnrollmentRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendancePolicyRepository;
import com.dawnrise.academic.studentattendance.policy.repository.StudentAttendanceStatusPolicyRepository;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionItemRepository;
import com.dawnrise.academic.studentattendance.correction.repository.StudentAttendanceCorrectionRequestRepository;
import com.dawnrise.academic.studentattendance.offlinesync.repository.StudentAttendanceOfflineSyncOperationRepository;
import com.dawnrise.academic.studentattendance.importing.repository.StudentAttendanceImportPreviewRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceRecordRepository;
import com.dawnrise.academic.studentattendance.recording.repository.StudentAttendanceSessionRepository;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionItemRepository;
import com.dawnrise.academic.studentprogression.repository.StudentProgressionOperationRepository;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.time.ZoneId;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=" +
				"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
				"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
				"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class AcademicServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		@Primary
		SchoolTimeZoneClient schoolTimeZoneClient() {
			return organizationId -> ZoneId.of("Asia/Kolkata");
		}

		@Bean
		StudentAttendanceImportPreviewRepository
		studentAttendanceImportPreviewRepository() {
			return (StudentAttendanceImportPreviewRepository)
					Proxy.newProxyInstance(
							StudentAttendanceImportPreviewRepository.class.getClassLoader(),
							new Class<?>[]{StudentAttendanceImportPreviewRepository.class},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentAttendanceImportPreviewRepositoryStub";
								}
								throw new UnsupportedOperationException(method.getName());
							}
					);
		}

		@Bean
		StudentAttendanceOfflineSyncOperationRepository
		studentAttendanceOfflineSyncOperationRepository() {
			return (StudentAttendanceOfflineSyncOperationRepository)
					Proxy.newProxyInstance(
							StudentAttendanceOfflineSyncOperationRepository.class.getClassLoader(),
							new Class<?>[]{StudentAttendanceOfflineSyncOperationRepository.class},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentAttendanceOfflineSyncOperationRepositoryStub";
								}
								throw new UnsupportedOperationException(method.getName());
							}
					);
		}

		@Bean
		AcademicYearStructureRolloverOperationRepository
		academicYearStructureRolloverOperationRepository() {
			return (AcademicYearStructureRolloverOperationRepository)
					Proxy.newProxyInstance(
							AcademicYearStructureRolloverOperationRepository
									.class
									.getClassLoader(),
							new Class<?>[]{
									AcademicYearStructureRolloverOperationRepository
											.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "AcademicYearStructureRolloverOperationRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		TransactionTemplate transactionTemplate() {
			return new TransactionTemplate(new PlatformTransactionManager() {
				@Override
				public TransactionStatus getTransaction(
						TransactionDefinition definition
				) throws TransactionException {
					return new SimpleTransactionStatus();
				}

				@Override
				public void commit(TransactionStatus status)
						throws TransactionException {
				}

				@Override
				public void rollback(TransactionStatus status)
						throws TransactionException {
				}
			});
		}

		@Bean
		AcademicYearRepository academicYearRepository() {
			return (AcademicYearRepository) Proxy.newProxyInstance(
					AcademicYearRepository.class.getClassLoader(),
					new Class<?>[]{AcademicYearRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "AcademicYearRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		AcademicCalendarDayRepository academicCalendarDayRepository() {
			return (AcademicCalendarDayRepository) Proxy.newProxyInstance(
					AcademicCalendarDayRepository.class.getClassLoader(),
					new Class<?>[]{AcademicCalendarDayRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "AcademicCalendarDayRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentAttendanceSessionRepository studentAttendanceSessionRepository() {
			return (StudentAttendanceSessionRepository) Proxy.newProxyInstance(
					StudentAttendanceSessionRepository.class.getClassLoader(),
					new Class<?>[]{StudentAttendanceSessionRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "StudentAttendanceSessionRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentAttendanceRecordRepository studentAttendanceRecordRepository() {
			return (StudentAttendanceRecordRepository) Proxy.newProxyInstance(
					StudentAttendanceRecordRepository.class.getClassLoader(),
					new Class<?>[]{StudentAttendanceRecordRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "StudentAttendanceRecordRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentAttendancePolicyRepository studentAttendancePolicyRepository() {
			return (StudentAttendancePolicyRepository) Proxy.newProxyInstance(
					StudentAttendancePolicyRepository.class.getClassLoader(),
					new Class<?>[]{StudentAttendancePolicyRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "StudentAttendancePolicyRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentAttendanceStatusPolicyRepository
		studentAttendanceStatusPolicyRepository() {
			return (StudentAttendanceStatusPolicyRepository)
					Proxy.newProxyInstance(
							StudentAttendanceStatusPolicyRepository.class
									.getClassLoader(),
							new Class<?>[]{
									StudentAttendanceStatusPolicyRepository.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentAttendanceStatusPolicyRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		StudentAttendanceCorrectionRequestRepository
		studentAttendanceCorrectionRequestRepository() {
			return (StudentAttendanceCorrectionRequestRepository)
					Proxy.newProxyInstance(
							StudentAttendanceCorrectionRequestRepository.class
									.getClassLoader(),
							new Class<?>[]{
									StudentAttendanceCorrectionRequestRepository.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentAttendanceCorrectionRequestRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		StudentAttendanceCorrectionItemRepository
		studentAttendanceCorrectionItemRepository() {
			return (StudentAttendanceCorrectionItemRepository)
					Proxy.newProxyInstance(
							StudentAttendanceCorrectionItemRepository.class
									.getClassLoader(),
							new Class<?>[]{
									StudentAttendanceCorrectionItemRepository.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentAttendanceCorrectionItemRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		GradeLevelRepository gradeLevelRepository() {
			return (GradeLevelRepository) Proxy.newProxyInstance(
					GradeLevelRepository.class.getClassLoader(),
					new Class<?>[]{GradeLevelRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "GradeLevelRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		SectionRepository sectionRepository() {
			return (SectionRepository) Proxy.newProxyInstance(
					SectionRepository.class.getClassLoader(),
					new Class<?>[]{SectionRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "SectionRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		SubjectRepository subjectRepository() {
			return (SubjectRepository) Proxy.newProxyInstance(
					SubjectRepository.class.getClassLoader(),
					new Class<?>[]{SubjectRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "SubjectRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		GradeLevelSubjectRepository gradeLevelSubjectRepository() {
			return (GradeLevelSubjectRepository) Proxy.newProxyInstance(
					GradeLevelSubjectRepository.class.getClassLoader(),
					new Class<?>[]{GradeLevelSubjectRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "GradeLevelSubjectRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		TeacherAssignmentRepository teacherAssignmentRepository() {
			return (TeacherAssignmentRepository) Proxy.newProxyInstance(
					TeacherAssignmentRepository.class.getClassLoader(),
					new Class<?>[]{TeacherAssignmentRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "TeacherAssignmentRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentEnrollmentRepository studentEnrollmentRepository() {
			return (StudentEnrollmentRepository) Proxy.newProxyInstance(
					StudentEnrollmentRepository.class.getClassLoader(),
					new Class<?>[]{StudentEnrollmentRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "StudentEnrollmentRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		StudentProgressionOperationRepository
		studentProgressionOperationRepository() {
			return (StudentProgressionOperationRepository)
					Proxy.newProxyInstance(
							StudentProgressionOperationRepository.class
									.getClassLoader(),
							new Class<?>[]{
									StudentProgressionOperationRepository.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentProgressionOperationRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		StudentProgressionItemRepository studentProgressionItemRepository() {
			return (StudentProgressionItemRepository)
					Proxy.newProxyInstance(
							StudentProgressionItemRepository.class
									.getClassLoader(),
							new Class<?>[]{
									StudentProgressionItemRepository.class
							},
							(proxy, method, args) -> {
								if (method.getName().equals("hashCode")) {
									return System.identityHashCode(proxy);
								}
								if (method.getName().equals("equals")) {
									return proxy == args[0];
								}
								if (method.getName().equals("toString")) {
									return "StudentProgressionItemRepositoryStub";
								}
								throw new UnsupportedOperationException(
										method.getName()
								);
							}
					);
		}

		@Bean
		@Primary
		IdentityTeacherEligibilityClient identityTeacherEligibilityClient() {
			return new IdentityTeacherEligibilityClient() {
				@Override
				public com.dawnrise.academic.teacherassignment.integration.identity.TeachingEligibilityResponse check(
						long organizationId,
						long userId
				) {
					throw new UnsupportedOperationException(
							"IdentityTeacherEligibilityClientStub"
					);
				}

				@Override
				public com.dawnrise.academic.teacherassignment.integration.identity.BatchTeachingEligibilityResponse checkBatch(
						long organizationId,
						java.util.List<Long> userIds
				) {
					throw new UnsupportedOperationException(
							"IdentityTeacherEligibilityClientStub"
					);
				}
			};
		}

		@Bean
		@Primary
		IdentityStudentEligibilityClient identityStudentEligibilityClient() {
			return new IdentityStudentEligibilityClient() {
				@Override
				public com.dawnrise.academic.studentenrollment.integration.identity.StudentEnrollmentEligibilityResponse check(
						long organizationId,
						long userId
				) {
					throw new UnsupportedOperationException(
							"IdentityStudentEligibilityClientStub"
					);
				}

				@Override
				public com.dawnrise.academic.studentenrollment.integration.identity.BatchStudentEnrollmentEligibilityResponse checkBatch(
						long organizationId,
						java.util.List<Long> userIds
				) {
					throw new UnsupportedOperationException(
							"IdentityStudentEligibilityClientStub"
					);
				}
			};
		}
	}

}
