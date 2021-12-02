
import { useQuery } from '@apollo/client';
import ClayButton from '@clayui/button';
import ClayIcon from '@clayui/icon';
import ClayModal from '@clayui/modal';
import ClayTable from '@clayui/table';
import React, {useState} from 'react';
import {getAccountSubscriptionsTerms} from '../../../../common/services/liferay/graphql/queries';
import {status} from '../../utils/constants';
import StatusTag from '../StatusTag';
import SubscriptionTermsModalPagination from '../SubscriptionTermsModalPagination';

const ModalCardSubscription = ({accountSubscriptionERC, observer, onClose}) => {
	const [activePage, setActivePage] = useState(1);
	const MAX_ITEMS_PER_PAGE = 5;

	const parseDate = (rawDate) => {
		const date = new Date(rawDate);
		const month = date.getMonth() + 1;
		const day = date.getDate();
		const year = date.getFullYear();

		return `${day}/${month < 10 ? `0${month}` : month}/${year}`;
	};

	const {
		data: subscriptionsTerms,
		loading: isSubscriptionsTermsLoading,
	} = useQuery(getAccountSubscriptionsTerms, {
		variables: {
			filter: `accountSubscriptionERC eq '${accountSubscriptionERC}'`,
			page: activePage,
			pageSize: MAX_ITEMS_PER_PAGE,
		},
	});

	const accountSubscriptionTermsItems =
		subscriptionsTerms?.c?.accountSubscriptionTerms?.items || [];

	return (
		<>
			{!isSubscriptionsTermsLoading && (
					<ClayModal center={true} observer={observer} size="lg">
						<div className="pt-4 px-4">
							<div className="d-flex justify-content-between mb-4 teste-cursor">
								<div className="flex-row mb-1">
									<h6 className="text-brand-primary">
										SUBSCRIPTION TERMS
									</h6>

									<h2 className="text-neutral-10">
										DXP Production
									</h2>
								</div>

								<ClayButton
									aria-label="close"
									className="close"
									displayType="unstyled"
									onClick={onClose}
								>
									<ClayIcon symbol="times" />
								</ClayButton>
							</div>

							<div>
								<ClayTable tableVerticalAlignment="middle">
									<ClayTable.Head>
										<ClayTable.Row>
											<ClayTable.Cell
												align="center"
												className="bg-neutral-1 font-weight-bold text-neutral-8"
												expanded
												headingCell
											>
												Start-End Date
											</ClayTable.Cell>

											<ClayTable.Cell
												align="center"
												className="bg-neutral-1 font-weight-bold text-neutral-8"
												headingCell
											>
												Provisioned
											</ClayTable.Cell>

											<ClayTable.Cell
												align="center"
												className="bg-neutral-1 font-weight-bold text-neutral-8"
												headingCell
											>
												Purchased
											</ClayTable.Cell>

											<ClayTable.Cell
												align="center"
												className="bg-neutral-1 font-weight-bold text-neutral-8"
												headingCell
											>
												Instance Size
											</ClayTable.Cell>

											<ClayTable.Cell
												align="center"
												className="bg-neutral-1 font-weight-bold text-neutral-8"
												headingCell
											>
												Status
											</ClayTable.Cell>
										</ClayTable.Row>
									</ClayTable.Head>

									<ClayTable.Body>
										{accountSubscriptionTermsItems.map(
											(item) => (
												<>
													<ClayTable.Row>
														<ClayTable.Cell align="center">
															{`${parseDate(
																item.startDate
															)} - ${parseDate(
																item.endDate
															)}`}
														</ClayTable.Cell>

														<ClayTable.Cell align="center">
															{item?.provisioned ||
																'-'}
														</ClayTable.Cell>

														<ClayTable.Cell align="center">
															{item.quantity}
														</ClayTable.Cell>

														<ClayTable.Cell align="center">
															{item.instanceSize}
														</ClayTable.Cell>

														<ClayTable.Cell align="center">
															{item.subscriptionTermStatus ===
																'Active' && (
																<StatusTag
																	currentStatus={
																		status.active
																	}
																/>
															)}

															{item.subscriptionTermStatus ===
																'Expired' && (
																<StatusTag
																	currentStatus={
																		status.expired
																	}
																/>
															)}

															{item.subscriptionTermStatus ===
																'Future' && (
																<StatusTag
																	currentStatus={
																		status.future
																	}
																/>
															)}
														</ClayTable.Cell>
													</ClayTable.Row>
												</>
											)
										)}
									</ClayTable.Body>
								</ClayTable>
							</div>
						</div>

						{accountSubscriptionTermsItems.length >
						MAX_ITEMS_PER_PAGE ? (
							<SubscriptionTermsModalPagination
								activePage={activePage}
								setActivePage={setActivePage}
								totalItems={
									accountSubscriptionTermsItems.length
								}
							/>
						) : (
							<p className="mb-4 mx-4 text-paragraph">{`Showing 1 to ${accountSubscriptionTermsItems.length} of ${accountSubscriptionTermsItems.length} entries.`}</p>
						)}
					</ClayModal>
			)}
		</>
	);
};

export default ModalCardSubscription;
