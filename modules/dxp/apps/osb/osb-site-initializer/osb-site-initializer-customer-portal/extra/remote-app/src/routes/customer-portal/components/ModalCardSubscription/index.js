import ClayButton from '@clayui/button';
import ClayIcon from '@clayui/icon';
import ClayModal from '@clayui/modal';
import ClayTable from '@clayui/table';
import { status } from '../../utils/constants';
import StatusTag from '../StatusTag';


const ModalCardSubscription = ({ observer, onClose }) => {

  return (
    <>
      <ClayModal
        observer={observer}
        size="lg"
      >

        <div className="p-4">
          <div className="d-flex justify-content-between mb-4 teste-cursor">
            <div className="flex-row mb-1">
              <h6 className="text-brand-primary">Subscription Terms</h6>

              <h2 className="text-neutral-10">DXP Production</h2>
            </div>

            <ClayButton
              aria-label="close"
              className="close"
              displayType="unstyled"
              onClick={onClose}
            >
              <ClayIcon
                symbol="times"
              />
            </ClayButton>
          </div>

          <div>
            <ClayTable tableVerticalAlignment="middle">
              <ClayTable.Head>
                <ClayTable.Row>

                  <ClayTable.Cell align="center" className="bg-neutral-1 font-weight-bold text-neutral-8" expanded headingCell>Start-End Date</ClayTable.Cell>

                  <ClayTable.Cell align="center" className="bg-neutral-1 font-weight-bold text-neutral-8" headingCell>Provisioned</ClayTable.Cell>

                  <ClayTable.Cell align="center" className="bg-neutral-1 font-weight-bold text-neutral-8" headingCell>Purchased</ClayTable.Cell>

                  <ClayTable.Cell align="center" className="bg-neutral-1 font-weight-bold text-neutral-8" headingCell>Instance Size</ClayTable.Cell>

                  <ClayTable.Cell align="center" className="bg-neutral-1 font-weight-bold text-neutral-8" headingCell>Status</ClayTable.Cell>

                </ClayTable.Row>
              </ClayTable.Head>

              <ClayTable.Body>

                <ClayTable.Row>
                  <ClayTable.Cell align="center" >6/10/2018 - 6/10/2021</ClayTable.Cell>

                  <ClayTable.Cell align="center" >3</ClayTable.Cell>

                  <ClayTable.Cell align="center" >5</ClayTable.Cell>

                  <ClayTable.Cell align="center" >1</ClayTable.Cell>

                  <ClayTable.Cell align="center" >

                    <StatusTag currentStatus={status.active} />

                  </ClayTable.Cell>
                </ClayTable.Row>

              </ClayTable.Body>
            </ClayTable>

          </div>
        </div>


      </ClayModal>
    </>
  )
}

export default ModalCardSubscription;


